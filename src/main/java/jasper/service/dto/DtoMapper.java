package jasper.service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.domain.proj.HasTags;
import jasper.security.Auth;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static jasper.repository.spec.QualifiedTag.concat;
import static jasper.repository.spec.QualifiedTag.selector;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	@Autowired
	ObjectMapper objectMapper;

	DateTimeFormatter smtp1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z Z", Locale.US);
	DateTimeFormatter smtp2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z z", Locale.US);

	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	@Mapping(target = "responses", source = "metadata.responses")
	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefNodeDto domainToNodeDto(Ref ref);

	public abstract RefReplDto domainToReplDto(Ref ref);

	public Ref smtpToDomain(SmtpWebhookDto msg) {
		var result = new Ref();
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
		result.setTags(emailToTags(msg));
		return result;
	}

	public List<String> emailToTags(SmtpWebhookDto msg) {
		var tags = new ArrayList<>(List.of("plugin/email"));
		if (msg.getAddresses() != null) {
			if (Optional.ofNullable(msg.getAddresses().getTo())
					.map(SmtpWebhookDto.EmailAddress::getAddress).isPresent()) {
				tags.add(emailAddressToNotification(msg.getAddresses().getTo().getAddress()));
			}
			if (Optional.ofNullable(msg.getAddresses().getFrom())
					.map(SmtpWebhookDto.EmailAddress::getAddress).isPresent()) {
				tags.add(emailAddressToNotification(msg.getAddresses().getFrom().getAddress()));
			}
			if (msg.getAddresses().getCc() != null) {
				for (var e : msg.getAddresses().getCc()) {
					if (e.getAddress() != null) {
						tags.add(emailAddressToNotification(e.getAddress()));
					}
				}
			}
			if (msg.getAddresses().getBcc() != null) {
				for (var e : msg.getAddresses().getBcc()) {
					if (e.getAddress() != null) {
						tags.add(emailAddressToNotification(e.getAddress()));
					}
				}
			}
			if (msg.getAddresses().getReplyTo() != null) {
				for (var e : msg.getAddresses().getReplyTo()) {
					if (e.getAddress() != null) {
						tags.add(emailAddressToNotification(e.getAddress()));
					}
				}
			}
		}
		return tags;
	}

	private String emailAddressToNotification(String email) {
		var qt = selector(email);
		if (auth.local(qt.origin)) {
			return concat("plugin/inbox", qt.tag);
		} else {
			return concat("plugin/outbox", qt.origin, qt.tag);
		}
	}

	@AfterMapping
	protected void filterTags(@MappingTarget HasTags ref) {
		if (ref.getTags() == null) return;
		var filtered = new ArrayList<>(auth.filterTags(ref.getTags()));
		if (ref.getPlugins() != null && filtered.size() < ref.getTags().size()) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.getPlugins().fieldNames().forEachRemaining(field -> {
				if (filtered.contains(field)) filteredPlugins.set(field, ref.getPlugins().get(field));
			});
			if (filteredPlugins.isEmpty()) {
				ref.setPlugins(null);
			} else {
				ref.setPlugins(filteredPlugins);
			}
		}
		ref.setTags(filtered);
		Ref.removePrefixTags(ref.getTags());
	}

	public abstract UserDto domainToDto(User user);

	@AfterMapping
	protected void filterTags(@MappingTarget UserDto userDto) {
		userDto.setReadAccess(auth.filterTags(userDto.getReadAccess()));
		userDto.setWriteAccess(auth.filterTags(userDto.getWriteAccess()));
	}

	@AfterMapping
	protected void userUrlsMetadata(Metadata source, @MappingTarget MetadataDto target) {
		if (source.getInternalResponses() == null) return;
		if (auth.getUserTag() == null) return;
		var start = "tag:/";
		var ending = "?user=" + auth.getUserTag().toString();
		target.setUserUrls(source.getInternalResponses().stream()
			.filter(url -> url.startsWith(start))
			.filter(url -> url.endsWith(ending))
			.map(url -> url.substring(start.length(), url.length() - ending.length()))
			.toList()
		);
	}

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}
}
