package jasper.client.dto;

import jasper.domain.Ref;
import jasper.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class JasperMapper {
	public abstract RefDto domainToDto(Ref ref);
	public abstract UserDto domainToDto(User user);
}
