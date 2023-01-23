package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.domain.User;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.NotFoundException;
import jasper.repository.UserRepository;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RolesDto;
import jasper.service.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.USER;

@Service
public class UserService {

	@Autowired
	UserRepository userRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.canWriteUser(#user)")
	@Timed(value = "jasper.service", extraTags = {"service", "user"}, histogram = true)
	public void create(User user) {
		if (userRepository.existsByQualifiedTag(user.getQualifiedTag())) throw new AlreadyExistsException();
		user.setModified(Instant.now());
		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "user"}, histogram = true)
	public UserDto get(String qualifiedTag) {
		var result = userRepository.findFirstByQualifiedTagOrderByModifiedDesc(qualifiedTag)
								   .orElseThrow(() -> new NotFoundException("User " + qualifiedTag));
		return mapper.domainToDto(result);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "user"}, histogram = true)
	public Page<UserDto> page(TagFilter filter, Pageable pageable) {
		return userRepository
			.findAll(
				auth.<User>tagReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.canWriteUser(#user)")
	@Timed(value = "jasper.service", extraTags = {"service", "user"}, histogram = true)
	public void update(User user) {
		var maybeExisting = userRepository.findFirstByQualifiedTagOrderByModifiedDesc(user.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException("User " + user.getQualifiedTag());
		user.addReadAccess(auth.hiddenTags(maybeExisting.get().getReadAccess()));
		user.addWriteAccess(auth.hiddenTags(maybeExisting.get().getWriteAccess()));
		user.setModified(Instant.now());
		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional
	@PreAuthorize("@auth.canWriteTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "user"}, histogram = true)
	public void delete(String tag) {
		try {
			userRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@Timed(value = "jasper.service", extraTags = {"service", "user"}, histogram = true)
	public RolesDto whoAmI() {
		return RolesDto
			.builder()
			.tag(auth.isLoggedIn() ? auth.getUserTag().toString() : "")
			.admin(auth.hasRole(ADMIN))
			.mod(auth.hasRole(MOD))
			.editor(auth.hasRole(EDITOR))
			.user(auth.hasRole(USER))
			.build();
	}
}
