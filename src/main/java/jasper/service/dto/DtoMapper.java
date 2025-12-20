package jasper.service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.Storage;
import jasper.domain.Ext;
import jasper.domain.External;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filtering mapper. Removes fields hidden to the user.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = false))
public abstract class DtoMapper {

	@Autowired
	Auth auth;

	@Autowired
	ObjectMapper objectMapper;

	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefDto domainToDto(Ref ref);

	@Mapping(target = "responses", source = "metadata.responses")
	@Mapping(target = "metadata.userUrls", ignore = true)
	public abstract RefNodeDto domainToNodeDto(Ref ref);

	public abstract RefReplDto dtoToRepl(RefDto ref);

	public abstract ExtDto domainToDto(Ext ext);

	public abstract UserDto domainToDto(User user);
	public abstract ExternalDto domainToDto(External external);

	public abstract PluginDto domainToDto(Plugin plugin);

	public abstract TemplateDto domainToDto(Template plugin);

	public abstract BackupDto domainToDto(Storage.StorageRef plugin);

	@AfterMapping
	protected void filterRefDtoTags(@MappingTarget RefDto.RefDtoBuilder target) {
		var ref = target.build();
		if (ref.tags() != null) {
			target.tags(new ArrayList<>(auth.filterTags(ref.tags())));
		}
	}

	@AfterMapping
	protected void filterRefDtoPlugins(@MappingTarget RefDto.RefDtoBuilder target) {
		var ref = target.build();
		if (ref.plugins() != null) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(tag -> {
				if (auth.canReadTag(tag + auth.getOrigin())) filteredPlugins.set(tag, ref.plugins().get(tag));
			});
			target.plugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
	}

	@AfterMapping
	protected void filterRefNodeDtoTags(@MappingTarget RefNodeDto.RefNodeDtoBuilder target) {
		var ref = target.build();
		if (ref.tags() != null) {
			target.tags(new ArrayList<>(auth.filterTags(ref.tags())));
		}
	}

	@AfterMapping
	protected void filterRefNodeDtoPlugins(@MappingTarget RefNodeDto.RefNodeDtoBuilder target) {
		var ref = target.build();
		if (ref.plugins() != null) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(tag -> {
				if (auth.canReadTag(tag + auth.getOrigin())) filteredPlugins.set(tag, ref.plugins().get(tag));
			});
			target.plugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
	}

	@AfterMapping
	protected void filterRefReplDtoTags(@MappingTarget RefReplDto.RefReplDtoBuilder target) {
		var ref = target.build();
		if (ref.tags() != null) {
			target.tags(new ArrayList<>(auth.filterTags(ref.tags())));
		}
	}

	@AfterMapping
	protected void filterRefReplDtoPlugins(@MappingTarget RefReplDto.RefReplDtoBuilder target) {
		var ref = target.build();
		if (ref.plugins() != null) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(tag -> {
				if (auth.canReadTag(tag + auth.getOrigin())) filteredPlugins.set(tag, ref.plugins().get(tag));
			});
			target.plugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
	}

	@AfterMapping
	protected void filterMetadata(@MappingTarget MetadataDto.MetadataDtoBuilder target, Metadata source) {
		var metadata = target.build();
		if (metadata.plugins() != null) {
			var filteredPlugins = new HashMap<String, Integer>();
			metadata.plugins().entrySet().forEach(e -> {
				if (auth.canReadTag(e.getKey() + auth.getOrigin())) {
					filteredPlugins.put(e.getKey(), e.getValue());
				}
			});
			target.plugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		
		// Handle userUrls
		if (source.userUrls() != null && auth.getUserTag() != null) {
			var prefix = "tag:/" + auth.getUserTag().tag + "?url=";
			var userUrls = source.userUrls().entrySet().stream()
				.filter(e -> e.getValue().stream().anyMatch(url -> url != null && url.startsWith(prefix)))
				.map(Map.Entry::getKey)
				.toList();
			target.userUrls(userUrls);
		}
	}

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}
}
