package jasper.component.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ref;
import jasper.service.dto.RefDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

@Mapper(componentModel = "spring")
public abstract class ComponentDtoMapper {

	@Autowired
	ObjectMapper objectMapper;

	public abstract RefUpdateDto domainToUpdateDto(Ref ref);

	@AfterMapping
	protected void publicTags(@MappingTarget RefUpdateDto ref) {
		if (ref.getTags() == null) return;
		var filtered = new ArrayList<>(ref.getTags().stream().filter(t -> !t.startsWith("_")).toList());
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
}
