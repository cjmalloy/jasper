package jasper.service;

import io.micrometer.core.annotation.Timed;
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

import java.time.Instant;

@Service
@Transactional
@PreAuthorize("@auth.sysMod()")
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
	Auth auth;

	@Timed(value = "jasper.service", extraTags = {"service", "origin"}, histogram = true)
	public void delete(String origin, Instant olderThan) {
		refRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		extRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		pluginRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		templateRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
		userRepository.deleteByOriginAndModifiedLessThanEqual(origin, olderThan);
	}
}
