package jasper.service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ext;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.HasTags;
import jasper.security.Auth;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	@Autowired
	ObjectMapper objectMapper;

	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	public abstract RefUpdateDto domainToUpdateDto(Ref ref);

	@AfterMapping
	protected void publicTags(@MappingTarget RefUpdateDto ref) {
		if (ref.getTags() == null) return;
		var filtered = new ArrayList<>(ref.getTags().stream().filter(t -> !t.startsWith("_")).toList());
		ref.setTags(filtered);
		Ref.removePrefixTags(ref.getTags());
	}

	@Mapping(target = "responses", source = "metadata.responses")
	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefNodeDto domainToNodeDto(Ref ref);

	public abstract RefReplDto domainToReplDto(Ref ref);

	public abstract ExtDto domainToDto(Ext ext);

	public abstract PluginDto domainToDto(Plugin plugin);

	public abstract TemplateDto domainToDto(Template plugin);

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
		if (source.getPlugins() == null) return;
		if (auth.getUserTag() == null) return;
		var prefix = "tag:/" + auth.getUserTag().tag + "?url=";
		target.setUserUrls(source.getPlugins().entrySet().stream()
			// TODO: how is null getting in here
			.filter(e -> e.getValue().stream().anyMatch(url -> url != null && url.startsWith(prefix)))
			.map(Map.Entry::getKey)
			.toList()
		);
	}

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}
}
