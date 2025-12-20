package jasper.component.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.service.dto.ExtDto;
import jasper.service.dto.PluginDto;
import jasper.service.dto.RefDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ComponentDtoMapper {

	@Autowired
	ObjectMapper objectMapper;

	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	public abstract UserDto domainToDto(User ref);

	public abstract ExtDto domainToDto(Ext ref);

	public abstract PluginDto domainToDto(Plugin ref);

	public abstract TemplateDto domainToDto(Template ref);

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}

	public abstract RefUpdateDto dtoToUpdateDto(RefDto ref);

	@AfterMapping
	protected RefUpdateDto publicTags(@MappingTarget RefUpdateDto ref) {
		if (ref.tags() == null) return ref;
		var filtered = new ArrayList<>(ref.tags().stream().filter(t -> !t.startsWith("_")).toList());
		RefUpdateDto result = ref.withTags(filtered);
		if (ref.plugins() != null && filtered.size() < ref.tags().size()) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(field -> {
				if (filtered.contains(field)) filteredPlugins.set(field, ref.plugins().get(field));
			});
			result = result.withPlugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		return result;
	}

	@AfterMapping
	protected MetadataUpdateDto publicMetadata(@MappingTarget MetadataUpdateDto metadata) {
		if (metadata.plugins() != null) {
			var filteredPlugins = new HashMap<String, Integer>();
			metadata.plugins().entrySet().forEach(e -> {
				if (!e.getKey().startsWith("_")) {
					filteredPlugins.put(e.getKey(), e.getValue());
				}
			});
			return metadata.withPlugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		return metadata;
	}
}
