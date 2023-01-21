package jasper.service.dto;

import jasper.domain.Ref;
import jasper.domain.User;
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

	public abstract RefDto domainToDto(Ref ref);

	@Mapping(target = "responses", source = "metadata.responses")
	public abstract RefNodeDto domainToNodeDto(Ref ref);

	public abstract RefReplDto domainToReplDto(Ref ref);

	@AfterMapping
	protected void filterTags(@MappingTarget RefDto refDto) {
		if (refDto.getTags() != null) {
			refDto.setTags(new ArrayList<>(auth.filterTags(refDto.getTags())));
		}
		Ref.removePrefixTags(refDto.getTags());
	}

	@AfterMapping
	protected void filterTags(@MappingTarget RefNodeDto refDto) {
		if (refDto.getTags() != null) {
			refDto.setTags(new ArrayList<>(auth.filterTags(refDto.getTags())));
		}
		Ref.removePrefixTags(refDto.getTags());
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
