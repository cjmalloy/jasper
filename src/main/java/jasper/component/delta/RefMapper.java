package jasper.component.delta;

import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.service.dto.PluginDto;
import jasper.service.dto.RefDto;
import jasper.service.dto.TemplateDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RefMapper {

	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	public abstract PluginDto domainToDto(Plugin ref);
	public abstract TemplateDto domainToDto(Template ref);

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
