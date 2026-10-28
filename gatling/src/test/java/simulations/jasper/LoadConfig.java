package simulations.jasper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Load test settings, read from system properties (-DLT_X=...) or environment variables (LT_X).
 *
 * Defaults target the docker compose stack in this directory.
 */
public final class LoadConfig {
	private LoadConfig() {}

	/**
	 * Debug secret shared with jasper-ui ?debug=ADMIN tokens and the compose stack (DO NOT USE IN PRODUCTION).
	 */
	static final String DEBUG_SECRET = "MjY0ZWY2ZTZhYmJhMTkyMmE5MTAxMTg3Zjc2ZDlmZWUwYjk0MDgzODA0MDJiOTgyNTk4MmNjYmQ4Yjg3MmVhYjk0MmE0OGFmNzE2YTQ5ZjliMTEyN2NlMWQ4MjA5OTczYjU2NzAxYTc4YThkMzYxNzdmOTk5MTIxODZhMTkwMDM=";
	static final String CSRF_TOKEN = "gatling-load-test";
	static final String ORIGIN_REGEX = "@[a-z0-9]+(?:[.][a-z0-9]+)*";

	public static String env(String name, String def) {
		var value = System.getProperty(name);
		if (value == null || value.isBlank()) value = System.getenv(name);
		return value == null || value.isBlank() ? def : value.trim();
	}

	public static int envInt(String name, int def) {
		return Integer.parseInt(env(name, "" + def));
	}

	public static double envDouble(String name, double def) {
		return Double.parseDouble(env(name, "" + def));
	}

	public static Duration envSeconds(String name, int def) {
		return Duration.ofSeconds(envInt(name, def));
	}

	/** Base URL of the server under test. */
	public static final String BASE_URL = env("LT_BASE_URL", "http://localhost:8081").replaceAll("/+$", "");
	public static final String WS_BASE_URL = BASE_URL.replaceFirst("^http", "ws");
	/**
	 * Static file server reachable from the server under test, used for proxy and scrape requests.
	 * Leave blank to skip proxy and scrape requests (always skipped in read-only runs).
	 */
	public static final String UPSTREAM = env("LT_UPSTREAM", "").replaceAll("/+$", "");
	/** Pre-issued bearer tokens, per role or for every role. */
	static final String TOKEN = env("LT_TOKEN", "");
	/** Base64 HS256 secret used to sign per-user tokens when no pre-issued token is set. Set to "none" for anonymous. */
	static final String JWT_SECRET = env("LT_JWT_SECRET", DEBUG_SECRET);
	/** Number of distinct virtual user names per role. */
	public static final int USERS = envInt("LT_USERS", 200);
	/** Multiplier for all think times. */
	public static final double THINK = envDouble("LT_THINK", 1.0);
	/** Topics used for subscriptions, tag pages and seeding. */
	public static final List<String> TOPICS = Arrays.stream(env("LT_TOPICS", String.join(",", Dataset.TOPICS)).split(","))
		.map(String::trim)
		.filter(t -> !t.isEmpty())
		.toList();

	/**
	 * Origin all requests are sent to (Local-Origin header).
	 * Writes are only ever made to a sub-origin so they can be removed with the Cleanup simulation.
	 */
	public static String origin(boolean readOnly) {
		return origin("LT_ORIGIN", readOnly);
	}

	/**
	 * Origin read from the given setting, defaulting to @loadtest, or the default origin when read only.
	 */
	public static String origin(String name, boolean readOnly) {
		var origin = env(name, readOnly ? "default" : "@loadtest").toLowerCase();
		if ("default".equals(origin)) origin = "";
		if (!origin.isEmpty() && !origin.matches(ORIGIN_REGEX)) {
			throw new IllegalArgumentException(name + " must be 'default' or match " + ORIGIN_REGEX + ": " + origin);
		}
		if (!readOnly && origin.isEmpty()) {
			throw new IllegalArgumentException("Simulations that write require " + name + " to be a sub-origin such as @loadtest");
		}
		return origin;
	}

	public static boolean anonymous() {
		return TOKEN.isEmpty() && "none".equals(JWT_SECRET)
			&& env("LT_USER_TOKEN", "").isEmpty()
			&& env("LT_MOD_TOKEN", "").isEmpty()
			&& env("LT_ADMIN_TOKEN", "").isEmpty();
	}

	/**
	 * Authorization header value for a virtual user.
	 * @param role USER, MOD or ADMIN
	 */
	public static String authorization(String user, String role) {
		var token = env("LT_" + role + "_TOKEN", TOKEN);
		if (token.isEmpty() && !"none".equals(JWT_SECRET)) token = jwt(user, role);
		return token.isEmpty() ? "" : "Bearer " + token;
	}

	/**
	 * Sign a token the same way jasper-ui does for ?debug=ROLE&tag=user.
	 */
	static String jwt(String user, String role) {
		var encoder = Base64.getUrlEncoder().withoutPadding();
		var header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		var claims = encoder.encodeToString(("{\"verified_email\":true,\"sub\":\"" + user + "\",\"auth\":\"ROLE_" + role + "\"}").getBytes(StandardCharsets.UTF_8));
		var body = header + "." + claims;
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(JWT_SECRET), "HmacSHA256"));
			return body + "." + encoder.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Could not sign JWT, check LT_JWT_SECRET", e);
		}
	}
}
