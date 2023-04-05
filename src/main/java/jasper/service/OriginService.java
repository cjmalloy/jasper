package jasper.service;

import com.rometools.rome.io.FeedException;
import io.micrometer.core.annotation.Timed;
import jasper.component.Replicator;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

@Service
@Transactional
public class OriginService {
	private static final Logger logger = LoggerFactory.getLogger(OriginService.class);

	@Autowired
	RefRepository refRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;
	@Autowired
	UserRepository userRepository;

	@Autowired
	Replicator replicator;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.hasRole('MOD') and @auth.local(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "origin"}, histogram = true)
	public void push(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		replicator.push(source);
	}

	@PreAuthorize("@auth.sysMod() and @auth.local(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "origin"}, histogram = true)
	public void pull(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		replicator.pull(source);
	}

	@PreAuthorize("@auth.sysMod()")
	@Timed(value = "jasper.service", extraTags = {"service", "origin"}, histogram = true)
	public void delete(String origin, Instant olderThan) {
		refRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		extRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		pluginRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		templateRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		userRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
	}
}
