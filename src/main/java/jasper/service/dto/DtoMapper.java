package jasper.service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	@Autowired
	ObjectMapper objectMapper;

	DateTimeFormatter smtp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z Z", Locale.US);

	public abstract RefDto domainToDto(Ref ref);

	@Mapping(target = "responses", source = "metadata.responses")
	public abstract RefNodeDto domainToNodeDto(Ref ref);

	public abstract RefReplDto domainToReplDto(Ref ref);

	public Ref smtpToDomain(SmtpWebhookDto msg) {
		var result = new Ref();
		result.setUrl("comment:" + UUID.randomUUID());
		if (!"0001-01-01 00:00:00 +0000 UTC".equals(msg.getDate())) {
			result.setPublished(ZonedDateTime.parse(msg.getDate(), smtp).toInstant());
		}
		result.setTitle(msg.getSubject());
		result.setComment(msg.getBody().getHtml() == null ? msg.getBody().getText() : msg.getBody().getHtml());
		result.setTags(new ArrayList<>(List.of("plugin/email")));
		return result;
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

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}
}
