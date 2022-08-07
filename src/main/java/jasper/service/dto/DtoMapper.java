package jasper.service.dto;

import jasper.domain.Feed;
import jasper.domain.Origin;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.security.Auth;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	@Mapping(target = "qualifiedNonPublicTags", ignore = true)
	@Mapping(target = "qualifiedTags", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	@AfterMapping
	protected void filterTags(@MappingTarget RefDto refDto) {
		refDto.setTags(auth.filterTags(refDto.getTags()));
		Ref.removePrefixTags(new ArrayList<>(refDto.getTags()));
	}

	@Mapping(target = "qualifiedNonPublicTags", ignore = true)
	@Mapping(target = "qualifiedTags", ignore = true)
	public abstract FeedDto domainToDto(Feed ref);

	@AfterMapping
	protected void filterTags(@MappingTarget FeedDto feedDto) {
		feedDto.setTags(auth.filterTags(feedDto.getTags()));
	}

	public abstract UserDto domainToDto(User user);

	@AfterMapping
	protected void filterTags(@MappingTarget UserDto userDto) {
		userDto.setReadAccess(auth.filterTags(userDto.getReadAccess()));
		userDto.setWriteAccess(auth.filterTags(userDto.getWriteAccess()));
	}

	public abstract OriginNameDto domainToDto(Origin origin);
}
