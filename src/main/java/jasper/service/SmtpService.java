package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.RefUrl;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.security.Auth;
import jasper.service.dto.SmtpWebhookDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static jasper.repository.spec.QualifiedTag.concat;
import static jasper.repository.spec.QualifiedTag.selector;

@Service
public class SmtpService {
	private static final Logger logger = LoggerFactory.getLogger(SmtpService.class);

	@Autowired
	Auth auth;

	DateTimeFormatter smtp1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z Z", Locale.US);
	DateTimeFormatter smtp2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z z", Locale.US);

	@Autowired
	RefService refService;

	@Autowired
	RefRepository refRepository;

	@Timed(value = "jasper.service", extraTags = {"service", "smtp"}, histogram = true)
	public void create(SmtpWebhookDto email, String origin) {
		var ref = smtpToDomain(email, origin);
		Page<Ref> source = refRepository.findAll(
				RefFilter.builder()
						.query("plugin/email:!internal")
						.endsTitle(ref.getTitle())
						.publishedBefore(ref.getPublished()).build().spec(),
				PageRequest.of(0, 1, Sort.by(Sort.Order.desc(Ref_.PUBLISHED))));
		if (!source.isEmpty()) {
			ref.setSources(List.of(source.getContent().get(0).getUrl()));
			ref.addTags(List.of("internal", "plugin/thread"));
		}
		refService.create(ref, false);
	}

	private Ref smtpToDomain(SmtpWebhookDto msg, String origin) {
		var result = new Ref();
		result.setOrigin(origin);
		result.setUrl("comment:" + UUID.randomUUID());
		if (!msg.getDate().startsWith("0001-01-01")) {
			try {
				result.setPublished(ZonedDateTime.parse(msg.getDate(), smtp1).toInstant());
			} catch (RuntimeException e) {
				result.setPublished(ZonedDateTime.parse(msg.getDate(), smtp2).toInstant());
			}
		}
		result.setTitle(msg.getSubject());
		result.setComment(msg.getBody().getHtml() == null ?
			msg.getBody().getText() :
			msg.getBody().getHtml().replaceAll("(?m)^\\s+", ""));
		result.setTags(emailToTags(msg, origin));
		return result;
	}

	private List<String> emailToTags(SmtpWebhookDto msg, String origin) {
		var tags = new ArrayList<>(List.of("plugin/email"));
		if (msg.getAddresses() != null) {
			if (Optional.ofNullable(msg.getAddresses().getTo())
				.map(SmtpWebhookDto.EmailAddress::getAddress).isPresent()) {
				tags.add(emailAddressToNotification(msg.getAddresses().getTo().getAddress(), origin));
			}
			if (Optional.ofNullable(msg.getAddresses().getFrom())
				.map(SmtpWebhookDto.EmailAddress::getAddress).isPresent()) {
				tags.add(emailAddressToNotification(msg.getAddresses().getFrom().getAddress(), origin));
			}
			if (msg.getAddresses().getCc() != null) {
				for (var e : msg.getAddresses().getCc()) {
					if (e.getAddress() != null) {
						tags.add(emailAddressToNotification(e.getAddress(), origin));
					}
				}
			}
			if (msg.getAddresses().getBcc() != null) {
				for (var e : msg.getAddresses().getBcc()) {
					if (e.getAddress() != null) {
						tags.add(emailAddressToNotification(e.getAddress(), origin));
					}
				}
			}
			if (msg.getAddresses().getReplyTo() != null) {
				for (var e : msg.getAddresses().getReplyTo()) {
					if (e.getAddress() != null) {
						tags.add(emailAddressToNotification(e.getAddress(), origin));
					}
				}
			}
		}
		return tags;
	}

	private String emailAddressToNotification(String email, String origin) {
		var qt = selector(email);
		var emailHost = qt.origin;
		var host = refRepository.originUrl(origin, origin)
			.map(RefUrl::get).map(this::getUri)
			.map(URI::getHost)
			.orElse(origin);
		if (emailHost.endsWith("." + host)) emailHost = emailHost.substring(0, emailHost.length() - host.length() - 1);
		var remote = refRepository.originUrl(origin, emailHost)
			.map(RefUrl::get)
			.map(this::getUri)
			.map(URI::getHost)
			.orElse(emailHost);
		if (auth.local(remote)) {
			return concat("plugin/inbox", qt.tag);
		} else {
			return concat("plugin/outbox", remote, qt.tag);
		}
	}

	private URI getUri(String url) {
		try {
			return new URI(url);
		} catch (URISyntaxException e) {
			return null;
		}
	}

}
