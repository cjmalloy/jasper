package ca.hc.jasper.service;

import java.time.Instant;

import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.dto.DtoMapper;
import ca.hc.jasper.service.dto.UserDto;
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
public class UserService {

	@Autowired
	UserRepository userRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.canWriteUser(#user)")
	public void create(User user) {
		if (userRepository.existsByQualifiedTag(user.getQualifiedTag())) throw new AlreadyExistsException();
		userRepository.save(user);
	}

	@PostAuthorize("@auth.canReadTag(#tag)")
	public UserDto get(String tag) {
		var result = userRepository.findOneByQualifiedTag(tag)
								   .orElseThrow(NotFoundException::new);
		return mapper.domainToDto(result);
	}

	public Page<UserDto> page(TagFilter filter, Pageable pageable) {
		return userRepository
			.findAll(
				auth.<User>tagReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.canWriteUser(#user)")
	public void update(User user) {
		var maybeExisting = userRepository.findOneByQualifiedTag(user.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		user.addReadAccess(auth.hiddenTags(maybeExisting.get().getReadAccess()));
		user.addWriteAccess(auth.hiddenTags(maybeExisting.get().getWriteAccess()));
		user.addSubscriptions(auth.hiddenTags(maybeExisting.get().getSubscriptions()));
		userRepository.save(user);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			userRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void clearNotifications(String tag) {
		var maybeExisting = userRepository.findOneByQualifiedTag(tag);
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var user = maybeExisting.get();
		user.setLastNotified(Instant.now());
		userRepository.save(user);
	}
}
