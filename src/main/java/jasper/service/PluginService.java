package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Plugin;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.PluginDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PluginService {

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.local(#plugin.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public void create(Plugin plugin) {
		if (pluginRepository.existsByQualifiedTag(plugin.getQualifiedTag())) throw new AlreadyExistsException();
		plugin.setModified(Instant.now());
		try {
			pluginRepository.save(plugin);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@PreAuthorize("@auth.local(#plugin.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public void push(Plugin plugin) {
		try {
			pluginRepository.save(plugin);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
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
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public boolean exists(String qualifiedTag) {
		return pluginRepository.existsByQualifiedTag(qualifiedTag);
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
	public void update(Plugin plugin) {
		var maybeExisting = pluginRepository.findOneByQualifiedTag(plugin.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Plugin " + plugin.getQualifiedTag());
		var existing = maybeExisting.get();
		if (!plugin.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Plugin " + plugin.getQualifiedTag());
		plugin.setModified(Instant.now());
		try {
			pluginRepository.save(plugin);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional
	@PreAuthorize("@auth.sysAdmin() or @auth.local(#qualifiedTag) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "plugin"}, histogram = true)
	public void delete(String qualifiedTag) {
		try {
			pluginRepository.deleteByQualifiedTag(qualifiedTag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
