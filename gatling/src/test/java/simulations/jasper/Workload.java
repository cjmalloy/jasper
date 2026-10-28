package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Requests modeled on what jasper-ui sends while a person browses.
 *
 * Every request only accepts its real success status (2xx, or 304 from the
 * browser cache), so rate limiting (429) and overload (503) are failures.
 */
public class Workload {
	static final List<Object> STABLE_DATE_SORT = List.of("published,DESC", "modified,ASC", "origin,ASC");
	static final String HIDE = ":!internal:!plugin/delete";
	static final String STOMP_CONNECT = "CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000";

	final boolean readOnly;
	final String origin;
	final double think;
	final double maxErrorPercent;
	final ErrorGuard guard = new ErrorGuard(200, 50);
	boolean crashOnErrors = true;
	/** When the error stop triggered, in epoch millis, or 0 if it never did. */
	final AtomicLong stoppedAt = new AtomicLong();

	/**
	 * @param readOnly never write (no writes, proxy or scrape requests)
	 * @param think multiplier for think times
	 * @param maxErrorPercent stop the run when the error rate of the last 200 requests exceeds this
	 */
	public Workload(boolean readOnly, double think, double maxErrorPercent) {
		this.readOnly = readOnly;
		this.origin = LoadConfig.origin(readOnly);
		this.think = think;
		this.maxErrorPercent = maxErrorPercent;
	}

	public HttpProtocolBuilder protocol(String name) {
		var protocol = http
			.baseUrl(BASE_URL)
			.wsBaseUrl(WS_BASE_URL)
			.acceptHeader("application/json")
			.contentTypeHeader("application/json")
			.userAgentHeader("Gatling Jasper Load Test - " + name)
			.header("X-XSRF-TOKEN", "#{xsrf}");
		if (!anonymous()) protocol = protocol.header("Authorization", "#{auth}");
		if (!origin.isEmpty()) protocol = protocol.header("Local-Origin", origin);
		return protocol;
	}

	// ====================== Session ======================

	/**
	 * Pick an identity and subscriptions. A few users are much more active than the rest.
	 */
	public ChainBuilder login(String role) {
		return exec(session -> {
			var random = ThreadLocalRandom.current();
			var user = Dataset.userName(role, Dataset.zipf(random, USERS, 1.0));
			var subs = new LinkedHashSet<String>();
			for (int i = 3 + random.nextInt(6); i > 0; i--) subs.add(Dataset.topic(random, TOPICS));
			return session
				.set("role", role)
				.set("auth", authorization(user, role))
				.set("userTag", "+user/" + user)
				.set("subs", new ArrayList<>(subs))
				.set("ws", random.nextDouble() < envDouble("LT_WS_SHARE", 0.3))
				.set("pageUrls", List.of())
				.set("pageRefs", List.of())
				.set("pageTags", List.of());
		})
			.exec(startCsrf());
	}

	/**
	 * What jasper-ui loads before showing the first page.
	 */
	public ChainBuilder appLoad() {
		return req(http("Whoami")
			.get("/api/v1/user/whoami")
			.check(jsonPath("$.tag").optional().saveAs("whoamiTag")))
			.exec(session -> {
				// Pre-issued tokens choose their own user, anonymous users only get an origin
				var tag = session.getString("whoamiTag");
				if (tag == null || tag.isEmpty() || tag.startsWith("@")) return session;
				return session.set("userTag", tag.split("@")[0]);
			})
			.exec(req(http("Load Plugins")
				.get("/api/v1/plugin/page")
				.queryParam("size", "500")))
			.exec(req(http("Load Templates")
				.get("/api/v1/template/page")
				.queryParam("size", "500")))
			.exec(req(http("Prefetch Exts")
				.get("/api/v1/ext/page")
				.queryParam("query", session -> extQuery(session.getList("subs")))
				.queryParam("size", "50")));
	}

	public ChainBuilder connectWebsocket() {
		return doIf("#{ws}").then(
			exec(ws("Connect WebSocket")
				.connect("/api/stomp/websocket")
				.onConnected(exec(ws("STOMP Connect")
					.sendText(STOMP_CONNECT)
					.await(10).on(ws.checkTextMessage("STOMP Connected").check(regex("^CONNECTED"))))
					.exec(ws("STOMP Subscribe")
						.sendText("SUBSCRIBE\nid:sub-0\ndestination:/topic/cursor/" + (origin.isEmpty() ? "default" : origin) + "\n\n\u0000")))));
	}

	public ChainBuilder closeWebsocket() {
		return doIf("#{ws}").then(exec(ws("Close WebSocket").close()));
	}

	public ChainBuilder think(int minSeconds, int maxSeconds) {
		return pause(
			Duration.ofMillis((long) (minSeconds * 1000 * think)),
			Duration.ofMillis((long) (maxSeconds * 1000 * think)));
	}

	/**
	 * Hard stop when the server is struggling, so a run against production backs off.
	 */
	public ChainBuilder stopOnErrors() {
		Function<Session, String> message = session -> "Stopping: " + guard.describe() + " exceeds LT_MAX_ERROR_PERCENT=" + maxErrorPercent;
		Function<Session, Boolean> condition = session -> {
			if (!guard.exceeds(maxErrorPercent)) return false;
			stoppedAt.compareAndSet(0, System.currentTimeMillis());
			return true;
		};
		return crashOnErrors
			? crashLoadGeneratorIf(message, condition)
			: stopLoadGeneratorIf(message, condition);
	}

	/**
	 * Stop the run normally instead of failing it when the error rate is exceeded.
	 */
	public Workload stopGracefully() {
		crashOnErrors = false;
		return this;
	}

	/**
	 * A person browsing for a while, with think time between page views.
	 */
	public ScenarioBuilder browse(String name, String role, int minViews, int maxViews) {
		return scenario(name)
			.exec(login(role))
			.exec(appLoad())
			.exec(connectWebsocket())
			.exec(homeFeed())
			.repeat(session -> ThreadLocalRandom.current().nextInt(minViews, maxViews + 1)).on(
				exec(think(3, 20))
					.exec(stopOnErrors())
					.exec(randomSwitch().on(actions(role))))
			.exec(closeWebsocket());
	}

	/**
	 * Mix of page views. Reads dominate; each write is a single request.
	 */
	List<Choice.WithWeight> actions(String role) {
		var choices = new ArrayList<Choice.WithWeight>();
		choices.add(percent(22.0).then(homeFeed()));
		choices.add(percent(20.0).then(tagFeed()));
		choices.add(percent(22.0).then(openRef()));
		choices.add(percent(8.0).then(search()));
		choices.add(percent(8.0).then(nextPage()));
		choices.add(percent(5.0).then(userFeed()));
		if (readOnly) {
			// Same reads, scaled up to fill the write share
			choices.add(percent(15.0).then(openRef()));
			return choices;
		}
		choices.add(percent(4.0).then(vote()));
		choices.add(percent(3.0).then(comment()));
		choices.add(percent(2.0).then(submitLink()));
		choices.add(percent(1.0).then(tagOwnRef()));
		choices.add(percent(1.0).then(editOwnRef()));
		switch (role) {
			case "MOD" -> choices.add(percent(4.0).then(editExt()));
			case "ADMIN" -> choices.add(percent(4.0).then(editTemplate()));
			default -> choices.add(percent(4.0).then(tagFeed()));
		}
		return choices;
	}

	// ====================== Reads ======================

	HttpRequestActionBuilder refPage(String name) {
		return http(name)
			.get("/api/v1/ref/page")
			.queryParam("obsolete", "false")
			.queryParam("size", "20")
			.check(jsonPath("$.content[*].url").findAll().withDefault(List.of()).saveAs("pageUrls"))
			// Default origin Refs omit the empty origin, so keep url and origin together
			.check(jsonPath("$.content[*]").ofMap().findAll().withDefault(List.of()).saveAs("pageRefs"))
			.check(jsonPath("$.content[*].tags[*]").findAll().withDefault(List.of()).saveAs("pageTags"));
	}

	/**
	 * Exts for the tags shown on the page, batched like jasper-ui.
	 */
	ChainBuilder showPage() {
		var chain = doIf(session -> !extQuery(session.getList("pageTags")).isEmpty()).then(
			req(http("Ext Batch")
				.get("/api/v1/ext/page")
				.queryParam("query", session -> extQuery(session.getList("pageTags")))
				.queryParam("size", "50")));
		if (readOnly || UPSTREAM.isEmpty()) return chain;
		// Embedded images on the page are loaded through the proxy
		return chain.exec(foreach(session -> images(session.getList("pageUrls")), "imageUrl").on(
			req(http("Proxy Image")
				.get("/api/v1/proxy")
				.queryParam("url", "#{imageUrl}")
				.queryParam("origin", origin)
				.header("Accept", "*/*"))));
	}

	public ChainBuilder homeFeed() {
		return randomSwitch().on(
			percent(60.0).then(subscriptionFeed()),
			percent(40.0).then(allFeed()));
	}

	public ChainBuilder subscriptionFeed() {
		return req(refPage("Home Feed")
			.queryParam("query", session -> "!internal:(" + String.join("|", session.<String>getList("subs")) + "):!plugin/delete")
			.queryParam("noUserResponse", "plugin/user/hide"))
			.exec(showPage());
	}

	public ChainBuilder allFeed() {
		return req(refPage("All Feed")
			.queryParam("query", "@*" + HIDE)
			.queryParam("noUserResponse", "plugin/user/hide"))
			.exec(showPage());
	}

	public ChainBuilder tagFeed() {
		return exec(session -> session.set("feedTag", pickTopic(session)))
			.exec(req(refPage("Tag Feed")
				.queryParam("query", "#{feedTag}" + HIDE)
				.queryParam("noUserResponse", "plugin/user/hide")
				.multivaluedQueryParam("sort", STABLE_DATE_SORT)))
			.exec(showPage());
	}

	public ChainBuilder nextPage() {
		return exec(session -> session.contains("feedTag") ? session : session.set("feedTag", pickTopic(session)))
			.exec(req(refPage("Tag Feed Next Page")
				.queryParam("query", "#{feedTag}" + HIDE)
				.queryParam("noUserResponse", "plugin/user/hide")
				.queryParam("page", "1")
				.multivaluedQueryParam("sort", STABLE_DATE_SORT)))
			.exec(showPage());
	}

	public ChainBuilder userFeed() {
		return exec(session -> {
			var users = session.<String>getList("pageTags").stream().filter(t -> t.startsWith("+user/")).toList();
			return session.set("feedUser", users.isEmpty() ? session.getString("userTag") : random(users));
		})
			.exec(req(refPage("User Feed")
				.queryParam("query", "#{feedUser}" + HIDE)
				.multivaluedQueryParam("sort", STABLE_DATE_SORT)))
			.exec(showPage());
	}

	public ChainBuilder search() {
		return exec(session -> session.set("searchText", Dataset.WORDS.get(Dataset.zipf(Dataset.WORDS.size()))))
			.exec(req(refPage("Search")
				.queryParam("query", "!internal:!plugin/delete")
				.queryParam("search", "#{searchText}")))
			.exec(showPage());
	}

	/**
	 * Open a Ref from the last page: the Ref, its comments, and sometimes its thread or graph.
	 */
	public ChainBuilder openRef() {
		return doIf(session -> !session.getList("pageRefs").isEmpty()).then(
			exec(getRef())
				.randomSwitch().on(
					percent(30.0).then(responses()),
					percent(5.0).then(graph())));
	}

	/** The Ref page and its comment thread. */
	public ChainBuilder getRef() {
		return exec(session -> {
			// Feeds such as All Feed include Refs from other origins
			Map<String, Object> ref = random(session.getList("pageRefs"));
			return session
				.set("openUrl", ref.get("url"))
				.set("openOrigin", ref.getOrDefault("origin", ""))
				.remove("openSources");
		})
			.exec(req(http("Get Ref")
				.get("/api/v1/ref")
				.queryParam("url", "#{openUrl}")
				.queryParam("origin", "#{openOrigin}")
				.check(jsonPath("$.sources[*]").findAll().optional().saveAs("openSources"))))
			.exec(req(http("Comments")
				.get("/api/v1/ref/page")
				.queryParam("query", "plugin/comment:!plugin/delete")
				.queryParam("responses", "#{openUrl}")
				.queryParam("obsolete", "false")
				.queryParam("size", "20")
				.multivaluedQueryParam("sort", List.of("published,ASC", "modified,ASC", "origin,ASC"))));
	}

	/** All responses to the open Ref. */
	public ChainBuilder responses() {
		return req(refPage("Responses")
			.queryParam("query", "!internal:!plugin/delete")
			.queryParam("responses", "#{openUrl}"));
	}

	/** Graph of the open Ref and its sources. */
	public ChainBuilder graph() {
		return req(http("Graph")
			.get("/api/v1/graph/list")
			.multivaluedQueryParam("urls", session -> {
				var urls = new ArrayList<Object>();
				urls.add(session.getString("openUrl"));
				if (session.contains("openSources")) urls.addAll(session.<Object>getList("openSources"));
				return urls.stream().distinct().limit(20).toList();
			}));
	}

	// ====================== Writes ======================

	/** Up-vote a Ref, stored as a user response. */
	public ChainBuilder vote() {
		return doIf(session -> !session.getList("pageUrls").isEmpty()).then(
			exec(session -> session.set("voteUrl", random(session.getList("pageUrls"))))
				.exec(req(http("Vote")
					.post("/api/v1/tags/response")
					.queryParam("tag", "plugin/user/vote/up")
					.queryParam("url", "#{voteUrl}")
					.check(status().is(201)))));
	}

	public ChainBuilder comment() {
		return doIf(session -> session.contains("openUrl")).then(
			exec(session -> session
				.set("commentUrl", "comment:" + UUID.randomUUID())
				.set("commentText", Dataset.text(ThreadLocalRandom.current(), 3)))
				.exec(req(http("Post Comment")
					.post("/api/v1/ref")
					.body(StringBody(session -> "{\"url\":" + Dataset.quote(session.getString("commentUrl")) +
						",\"origin\":" + Dataset.quote(origin) +
						",\"comment\":" + Dataset.quote(session.getString("commentText")) +
						",\"tags\":" + Dataset.array(List.of("public", "internal", "plugin/comment", session.getString("userTag"))) +
						",\"sources\":" + Dataset.array(List.of(session.getString("openUrl"), session.getString("openUrl"))) + "}"))
					.check(status().is(201)))));
	}

	/**
	 * Submit a link: scrape it for a title, check it does not exist, then post it.
	 */
	public ChainBuilder submitLink() {
		var chain = exec(session -> {
			var random = ThreadLocalRandom.current();
			return session
				.set("submitUrl", "https://loadtest.example.com/submit/" + UUID.randomUUID())
				.set("submitTitle", Dataset.title(random))
				.set("submitTopic", Dataset.topic(random, TOPICS));
		});
		if (!UPSTREAM.isEmpty()) {
			chain = chain.exec(req(http("Scrape Link")
				.get("/api/v1/scrape/web")
				.queryParam("url", UPSTREAM + "/article.html")));
		}
		return chain
			.exec(req(http("Link Exists")
				.get("/api/v1/ref/count")
				.queryParam("url", "#{submitUrl}")
				.queryParam("obsolete", "false")
				.check(bodyString().is("0"))))
			.exec(req(http("Submit Link")
				.post("/api/v1/ref")
				.body(StringBody(session -> "{\"url\":" + Dataset.quote(session.getString("submitUrl")) +
					",\"origin\":" + Dataset.quote(origin) +
					",\"title\":" + Dataset.quote(session.getString("submitTitle")) +
					",\"tags\":" + Dataset.array(List.of("public", session.getString("userTag"), session.getString("submitTopic"))) + "}"))
				.check(status().is(201))
				.check(jsonPath("$").saveAs("ownCursor"))))
			.exec(session -> session.set("ownUrl", session.getString("submitUrl")));
	}

	/** Toggle a tag on a Ref the user submitted. */
	public ChainBuilder tagOwnRef() {
		return doIf(session -> session.contains("ownUrl")).then(
			exec(session -> session.set("toggleTag", (ThreadLocalRandom.current().nextBoolean() ? "" : "-") + Dataset.topic(ThreadLocalRandom.current(), TOPICS)))
				.exec(req(http("Toggle Tag")
					.patch("/api/v1/tags")
					.queryParam("tags", "#{toggleTag}")
					.queryParam("url", "#{ownUrl}")
					.queryParam("origin", origin)
					.check(jsonPath("$").saveAs("ownCursor")))));
	}

	/** Edit the comment of a Ref the user submitted. */
	public ChainBuilder editOwnRef() {
		return doIf(session -> session.contains("ownUrl")).then(
			exec(req(http("Edit Ref")
				.patch("/api/v1/ref")
				.queryParam("url", "#{ownUrl}")
				.queryParam("origin", origin)
				.queryParam("cursor", "#{ownCursor}")
				.header("Content-Type", "application/json-patch+json")
				.body(StringBody(session -> "[{\"op\":\"add\",\"path\":\"/comment\",\"value\":" + Dataset.quote(Dataset.text(ThreadLocalRandom.current(), 2)) + "}]"))
				.check(jsonPath("$").saveAs("ownCursor")))));
	}

	/** Moderators rename topics, from a fixed set. */
	public ChainBuilder editExt() {
		return exec(session -> session.set("extTag", random(TOPICS)))
			.exec(req(http("Get Ext")
				.get("/api/v1/ext")
				.queryParam("tag", "#{extTag}" + origin)
				.check(jsonPath("$.modified").saveAs("extCursor"))))
			.exec(req(http("Edit Ext")
				.patch("/api/v1/ext")
				.queryParam("tag", "#{extTag}" + origin)
				.queryParam("cursor", "#{extCursor}")
				.header("Content-Type", "application/merge-patch+json")
				.body(StringBody(session -> "{\"name\":" + Dataset.quote(Dataset.displayName(session.getString("extTag")) + " " + ThreadLocalRandom.current().nextInt(100)) + "}"))));
	}

	/** Admins tweak the single load test template. */
	public ChainBuilder editTemplate() {
		return exec(req(http("Get Template")
			.get("/api/v1/template")
			.queryParam("tag", "kb" + origin)
			.check(jsonPath("$.modified").saveAs("templateCursor"))))
			.exec(req(http("Edit Template")
				.patch("/api/v1/template")
				.queryParam("tag", "kb" + origin)
				.queryParam("cursor", "#{templateCursor}")
				.header("Content-Type", "application/merge-patch+json")
				.body(StringBody(session -> "{\"name\":\"Knowledge Base " + ThreadLocalRandom.current().nextInt(100) + "\"}"))));
	}

	// ====================== Helpers ======================

	/**
	 * Start a double submit CSRF cookie. The server rotates it on every response.
	 */
	static ChainBuilder startCsrf() {
		return exec(addCookie(Cookie("XSRF-TOKEN", CSRF_TOKEN).withPath("/").withSecure(false)))
			.exec(session -> session.set("xsrf", CSRF_TOKEN));
	}

	/**
	 * Copy the current CSRF cookie into the X-XSRF-TOKEN header, like jasper-ui does before each request.
	 */
	static ChainBuilder csrf() {
		return exec(getCookieValue(CookieKey("XSRF-TOKEN").saveAs("xsrf")));
	}

	/**
	 * Execute a request and record its outcome for {@link #stopOnErrors()}.
	 */
	ChainBuilder req(HttpRequestActionBuilder request) {
		if (maxErrorPercent >= 100) return exec(csrf()).exec(request);
		return exec(csrf()).exec(request).exec(session -> {
			guard.record(!session.isFailed());
			return session.isFailed() ? session.markAsSucceeded() : session;
		});
	}

	String extQuery(List<String> tags) {
		return tags.stream()
			.filter(t -> !t.startsWith("_"))
			.distinct()
			.limit(20)
			.map(t -> t + origin)
			.reduce((a, b) -> a + "|" + b)
			.orElse("");
	}

	static List<String> images(List<String> urls) {
		return urls.stream().filter(u -> u.startsWith(UPSTREAM + "/")).limit(2).toList();
	}

	static String pickTopic(Session session) {
		var tags = session.<String>getList("pageTags").stream()
			.filter(t -> !t.startsWith("_") && !t.startsWith("+") && !t.equals("public") && !t.equals("internal"))
			.toList();
		if (tags.isEmpty() || ThreadLocalRandom.current().nextDouble() < 0.3) return Dataset.topic(ThreadLocalRandom.current(), TOPICS);
		return random(tags);
	}

	static <T> T random(List<T> list) {
		return list.get(ThreadLocalRandom.current().nextInt(list.size()));
	}

	/**
	 * Error rate over a sliding window of recent requests.
	 */
	static final class ErrorGuard {
		private final boolean[] failures;
		private final int minSamples;
		private int index, count, failed;

		ErrorGuard(int window, int minSamples) {
			this.failures = new boolean[window];
			this.minSamples = minSamples;
		}

		synchronized void record(boolean ok) {
			if (count == failures.length) {
				if (failures[index]) failed--;
			} else {
				count++;
			}
			failures[index] = !ok;
			if (!ok) failed++;
			index = (index + 1) % failures.length;
		}

		synchronized boolean exceeds(double percent) {
			return count >= minSamples && failed * 100.0 / count > percent;
		}

		synchronized String describe() {
			return failed + " failures in the last " + count + " requests";
		}
	}
}
