package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.component.IngestPlugin;
import jasper.domain.Plugin;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.PluginDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PluginService {

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	IngestPlugin ingest;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.local(#plugin.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public Instant create(Plugin plugin) {
		ingest.create(plugin);
		return plugin.getModified();
	}

	@PreAuthorize("@auth.local(#plugin.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public void push(Plugin plugin) {
		ingest.push(plugin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public PluginDto get(String qualifiedTag) {
		return pluginRepository.findOneByQualifiedTag(qualifiedTag)
			.map(mapper::domainToDto)
			.orElseThrow(() -> new NotFoundException("Plugin " + qualifiedTag));
	}

	@Transactional(readOnly = true)
	@PreAuthorize( "@auth.hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public Instant cursor(String origin) {
		return pluginRepository.getCursor(origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public Page<PluginDto> page(TagFilter filter, Pageable pageable) {
		return pluginRepository
			.findAll(
				auth.<Plugin>tagReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.local(#plugin.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public Instant update(Plugin plugin) {
		ingest.update(plugin);
		return plugin.getModified();
	}

	@Transactional
	@PreAuthorize("@auth.subOrigin(#qualifiedTag) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public void delete(String qualifiedTag) {
		try {
			ingest.delete(qualifiedTag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
