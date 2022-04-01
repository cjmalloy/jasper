package ca.hc.jasper.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import ca.hc.jasper.domain.Plugin;
import ca.hc.jasper.repository.PluginRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PluginService {

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('ADMIN')")
	public void create(Plugin plugin) {
		if (pluginRepository.existsByQualifiedTag(plugin.getQualifiedTag())) throw new AlreadyExistsException();
		pluginRepository.save(plugin);
	}

	@PostAuthorize("@auth.canReadTag(#tag)")
	public Plugin get(String tag) {
		return pluginRepository.findOneByQualifiedTag(tag)
							   .orElseThrow(NotFoundException::new);
	}

	public Page<Plugin> page(TagFilter filter, Pageable pageable) {
		return pluginRepository
			.findAll(
				auth.<Plugin>tagReadSpec()
					.and(filter.spec()),
				pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#plugin.qualifiedTag)")
	public void update(Plugin plugin) {
		var maybeExisting = pluginRepository.findOneByQualifiedTag(plugin.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!plugin.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException();
		plugin.setModified(Instant.now());
		pluginRepository.save(plugin);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			pluginRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
