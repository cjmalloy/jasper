package jasper.component.delta;

import jasper.domain.Ref;
import jasper.service.dto.RefDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RefMapper {

	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	@AfterMapping
	protected void filterTags(@MappingTarget RefDto ref) {
		if (ref.getTags() == null) return;
		Ref.removePrefixTags(ref.getTags());
	}

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}
}
