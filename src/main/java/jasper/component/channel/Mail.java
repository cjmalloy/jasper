package jasper.component.channel;

import io.vavr.Tuple;
import jasper.component.scheduler.Async;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.domain.proj.RefUrl;
import jasper.repository.RefRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Profile("smtp-relay")
@Component
public class Mail implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Mail.class);

	@Autowired
	Props props;

	@Autowired
	Async async;

	@Autowired
	RefRepository refRepository;

	@Autowired
	JavaMailSender emailSender;

	@PostConstruct
	void init() {
		async.addAsyncTag("plugin/email", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		if (!ref.getTags().contains("+user") && !ref.getTags().contains("_user")) return;
		var ts = new ArrayList<>(ref.getTags());
		ref.removePrefixTags(ts);
		var tos = ts.stream()
			.filter(t -> t.startsWith("plugin/outbox/"))
			.map(t -> t.substring("plugin/outbox/".length()))
			.map(t -> Tuple.of(t.substring(0, t.indexOf("/")), t.substring(t.indexOf("/") + 1)))
			.map(to -> to._2 + "@" + refRepository.originUrl(ref.getOrigin(), to._1).map(RefUrl::get).orElse(to._1))
			.toArray(String[]::new);
		if (tos.length == 0) {
			logger.error("No recipients for email.");
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(tos);
		var host = refRepository.originUrl(ref.getOrigin(), ref.getOrigin()).map(RefUrl::get).map(str -> {
			try {
				return new URI(str);
			} catch (URISyntaxException e) {
				return null;
			}
		}).map(URI::getHost).orElse(Stream.of(ref.getOrigin(), props.getEmailHost()).filter(StringUtils::isNotBlank).collect(Collectors.joining(".")));
		message.setFrom(ts.stream()
			.filter(t -> t.startsWith("+user/") || t.startsWith("+user") || t.startsWith("_user/") || t.startsWith("_user"))
			.findFirst()
			.map(t -> t + "@" + host)
			.orElse("no-reply@" + host)
		);
		message.setSubject(ref.getTitle());
		message.setText(ref.getComment());
		emailSender.send(message);
	}
}
