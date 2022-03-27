package ca.hc.jasper.service.dto;

import ca.hc.jasper.domain.*;
import ca.hc.jasper.security.Auth;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	public abstract RefDto domainToDto(Ref ref);
	public abstract QueueDto domainToDto(Queue queue);
	public abstract UserDto domainToDto(User user);

	@AfterMapping
	protected void filterTags(@MappingTarget RefDto refDto) {
		refDto.setTags(auth.filterTags(refDto.getTags()));
	}

	@AfterMapping
	protected void filterTags(@MappingTarget QueueDto queueDto) {
		queueDto.setApprovers(auth.filterTags(queueDto.getApprovers()));
	}

	@AfterMapping
	protected void filterTags(@MappingTarget UserDto userDto) {
		userDto.setSubscriptions(auth.filterTags(userDto.getSubscriptions()));
		userDto.setReadAccess(auth.filterTags(userDto.getReadAccess()));
		userDto.setWriteAccess(auth.filterTags(userDto.getWriteAccess()));
	}
}
