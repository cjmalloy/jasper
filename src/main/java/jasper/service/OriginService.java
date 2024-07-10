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
import java.time.Duration;
import java.time.Instant;

import static jasper.security.AuthoritiesConstants.ADMIN;

@Service
public class OriginService {
	private static final Logger logger = LoggerFactory.getLogger(OriginService.class);

	@Autowired
	RefRepository refRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;

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

	@PreAuthorize("@auth.hasRole('MOD') and @auth.local(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "origin"}, histogram = true)
	public void pull(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		replicator.pull(source);
	}

	@Transactional
	@PreAuthorize("@auth.hasRole('MOD') and @auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "origin"}, histogram = true)
	public void delete(String origin, Instant olderThan) {
		var start = Instant.now();
		logger.info("{} Deleting origin {} older than {}", origin, origin, olderThan);
		refRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		extRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		userRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		if (!auth.local(origin) || auth.hasRole(ADMIN)) {
			pluginRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
			templateRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		}
		logger.info("{} Finished deleting origin {} older than {} in {}", origin, origin, olderThan, Duration.between(start, Instant.now()));
	}
}
