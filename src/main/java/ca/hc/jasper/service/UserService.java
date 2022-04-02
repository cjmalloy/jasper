package ca.hc.jasper.service;

import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.dto.DtoMapper;
import ca.hc.jasper.service.dto.UserDto;
import ca.hc.jasper.service.errors.AlreadyExistsException;
import ca.hc.jasper.service.errors.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	@PreAuthorize("@auth.canReadTag(#tag)")
	public UserDto get(String tag) {
		var result = userRepository.findOneByQualifiedTag(tag)
								   .orElseThrow(NotFoundException::new);
		return mapper.domainToDto(result);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
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
}
