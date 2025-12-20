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
@Mapper(componentModel = "spring")
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
	protected RefDto filterRefDtoTags(@MappingTarget RefDto ref) {
		if (ref.tags() != null) {
			return ref.withTags(new ArrayList<>(auth.filterTags(ref.tags())));
		}
		return ref;
	}

	@AfterMapping
	protected RefDto filterRefDtoPlugins(@MappingTarget RefDto ref) {
		if (ref.plugins() != null) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(tag -> {
				if (auth.canReadTag(tag + auth.getOrigin())) filteredPlugins.set(tag, ref.plugins().get(tag));
			});
			return ref.withPlugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		return ref;
	}

	@AfterMapping
	protected RefNodeDto filterRefNodeDtoTags(@MappingTarget RefNodeDto ref) {
		if (ref.tags() != null) {
			return ref.withTags(new ArrayList<>(auth.filterTags(ref.tags())));
		}
		return ref;
	}

	@AfterMapping
	protected RefNodeDto filterRefNodeDtoPlugins(@MappingTarget RefNodeDto ref) {
		if (ref.plugins() != null) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(tag -> {
				if (auth.canReadTag(tag + auth.getOrigin())) filteredPlugins.set(tag, ref.plugins().get(tag));
			});
			return ref.withPlugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		return ref;
	}

	@AfterMapping
	protected RefReplDto filterRefReplDtoTags(@MappingTarget RefReplDto ref) {
		if (ref.tags() != null) {
			return ref.withTags(new ArrayList<>(auth.filterTags(ref.tags())));
		}
		return ref;
	}

	@AfterMapping
	protected RefReplDto filterRefReplDtoPlugins(@MappingTarget RefReplDto ref) {
		if (ref.plugins() != null) {
			var filteredPlugins = objectMapper.createObjectNode();
			ref.plugins().fieldNames().forEachRemaining(tag -> {
				if (auth.canReadTag(tag + auth.getOrigin())) filteredPlugins.set(tag, ref.plugins().get(tag));
			});
			return ref.withPlugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		return ref;
	}

	@AfterMapping
	protected MetadataDto filterMetadata(Metadata source, @MappingTarget MetadataDto metadata) {
		MetadataDto result = metadata;
		if (metadata.plugins() != null) {
			var filteredPlugins = new HashMap<String, Integer>();
			metadata.plugins().entrySet().forEach(e -> {
				if (auth.canReadTag(e.getKey() + auth.getOrigin())) {
					filteredPlugins.put(e.getKey(), e.getValue());
				}
			});
			result = result.withPlugins(filteredPlugins.isEmpty() ? null : filteredPlugins);
		}
		
		// Handle userUrls
		if (source.userUrls() != null && auth.getUserTag() != null) {
			var prefix = "tag:/" + auth.getUserTag().tag + "?url=";
			var userUrls = source.userUrls().entrySet().stream()
				.filter(e -> e.getValue().stream().anyMatch(url -> url != null && url.startsWith(prefix)))
				.map(Map.Entry::getKey)
				.toList();
			result = result.withUserUrls(userUrls);
		}
		return result;
	}

	@AfterMapping
	protected UserDto filterTags(@MappingTarget UserDto userDto) {
		return userDto
			.withReadAccess(auth.filterTags(userDto.readAccess()))
			.withWriteAccess(auth.filterTags(userDto.writeAccess()));
	}

	public int countMetadata(List<String> responses) {
		if (responses == null) return 0;
		return responses.size();
	}
}
