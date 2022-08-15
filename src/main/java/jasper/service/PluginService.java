package jasper.service;

import jasper.domain.Plugin;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ForeignWriteException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
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

	@PreAuthorize("hasRole('ADMIN')")
	public void create(Plugin plugin) {
		if (!auth.local(plugin.getOrigin())) throw new ForeignWriteException(plugin.getOrigin());
		if (pluginRepository.existsByQualifiedTag(plugin.getQualifiedTag())) throw new AlreadyExistsException();
		try {
			pluginRepository.save(plugin);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#tag)")
	public Plugin get(String tag) {
		return pluginRepository.findOneByQualifiedTag(tag)
							   .orElseThrow(() -> new NotFoundException("Plugin " + tag));
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#tag)")
	public boolean exists(String tag) {
		return pluginRepository.existsByQualifiedTag(tag);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	public Page<Plugin> page(TagFilter filter, Pageable pageable) {
		return pluginRepository
			.findAll(
				auth.<Plugin>tagReadSpec()
					.and(filter.spec()),
				pageable);
	}

	@PreAuthorize("hasRole('ADMIN')")
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
	@PreAuthorize("hasRole('ADMIN')")
	public void delete(String tag) {
		try {
			pluginRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
