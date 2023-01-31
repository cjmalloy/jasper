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

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	@Autowired
	ObjectMapper objectMapper;

	public abstract RefDto domainToDto(Ref ref);

	@Mapping(target = "responses", source = "metadata.responses")
	public abstract RefNodeDto domainToNodeDto(Ref ref);

	public abstract RefReplDto domainToReplDto(Ref ref);

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
