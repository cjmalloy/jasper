package jasper.domain.proj;

import jasper.domain.Ref;
import jasper.domain.User;

import java.util.ArrayList;
import java.util.List;

public interface Tag extends HasOrigin {
	String REGEX = "[_+]?[a-z0-9]+(?:[./][a-z0-9]+)*";
	String QTAG_REGEX = REGEX + HasOrigin.REGEX;
	String ADD_REMOVE_REGEX = "[-]?" + REGEX;
	int TAG_LEN = 64;
	int QTAG_LEN = TAG_LEN + ORIGIN_LEN + 1;

	String getTag();
	void setTag(String tag);
	String getName();

	default String getQualifiedTag() {
		return getTag() + getOrigin();
	}

	static String urlForUser(String url, String user) {
		return "tag:/" + user + "?url=" + url;
	}

	static void removeTag(List<String> tags, String tag) {
		for (var i = tags.size() - 1; i >= 0; i--) {
			var t = tags.get(i);
			if (t.equals(tag) || t.startsWith(tag + "/")) {
				tags.remove(i);
			}
		}
	}

	static List<String> replyTags(Ref ref) {
		var tags = new ArrayList<String>();
		if (ref.getTags().contains("public")) tags.add("public");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("dm")) tags.add("dm");
		if (ref.getTags().contains("dm")) tags.add("internal");
		if (ref.getTags().contains("dm")) tags.add("plugin/thread");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("plugin/comment")) tags.add("internal");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/thread");
		ref.getTags().stream().filter(User::isUser).findFirst().ifPresent(author ->
			tags.add("plugin/inbox/" + author.substring(1)));
		for (var t : ref.getTags()) {
			if (t.startsWith("plugin/inbox/") || t.startsWith("plugin/outbox/")) {
				tags.add(t);
			}
		}
		return tags;
	}

	static List<String> replySources(Ref ref) {
		var sources = new ArrayList<>(List.of(ref.getUrl()));
		if (ref.getTags().contains("plugin/thread")) {
			// Add top comment source
			if (ref.getSources() != null && ref.getSources().size() > 0) {
				if (ref.getSources().size() > 1) {
					sources.add(ref.getSources().get(1));
				} else {
					sources.add(ref.getSources().get(0));
				}
			}
		}
		return sources;
	}
}
