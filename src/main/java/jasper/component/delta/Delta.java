package jasper.component.delta;

import jasper.component.Ingest;
import jasper.component.scheduler.Async;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.Tag;
import jasper.repository.RefRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static jasper.domain.proj.Tag.replySources;
import static jasper.domain.proj.Tag.replyTags;
import static jasper.repository.spec.RefSpec.isUrls;

public abstract class Delta implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Delta.class);

	public final String response;

	private final Ingest ingest;
	private final RefRepository refRepository;

	public Delta(String response, Ingest ingest, RefRepository refRepository) {
		this.response = response;
		this.ingest = ingest;
		this.refRepository = refRepository;
	}

	public abstract DeltaReply transform(Ref ref, List<Ref> sources);

	@Override
	public void run(Ref ref) {
		if (ref.hasPluginResponse(response)) return;
		var res = transform(ref, refRepository.findAll(isUrls(ref.getSources())));
		res.res().addTags(replyTags(ref));
		res.res().setSources(replySources(ref));
		for (var reply : res.ref) {
			reply.setOrigin(ref.getOrigin());
			if (reply.getTags() != null) {
				reply.setTags(new ArrayList<>(reply.getTags().stream().filter(
					t -> t.matches(Tag.REGEX) && (t.equals("+plugin/ai") || !t.startsWith("+") && !t.startsWith("_"))
				).toList()));
			}

			ingest.ingest(reply, false);
		}
		// TODO: ingest ext, plugin, template, user
	}

	@Getter
	@Setter
	public static class DeltaReply {
		private Ref[] ref;
		private Ext[] ext;
		private Plugin[] plugin;
		private Template[] template;
		private User[] user;

		public static DeltaReply of(Ref response) {
			var result = new DeltaReply();
			result.ref = new Ref[]{response};
			return result;
		}

		public Ref res() {
			return ref[0];
		}
	}
}
