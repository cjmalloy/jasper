package ca.hc.jasper.service;

import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

	@Autowired
	UserRepository userRepository;

	// TODO: prevent setting permissions
	@PreAuthorize("@auth.canWriteTag(#user.tag)")
	public void create(User user) {
		if (!user.local()) throw new ForeignWriteException();
		if (userRepository.existsByTagAndOrigin(user.getTag(), user.getOrigin())) throw new AlreadyExistsException();
		userRepository.save(user);
	}

	@PostAuthorize("@auth.canReadTag(returnObject)")
	public User get(String tag, String origin) {
		return userRepository.findOneByTagAndOrigin(tag, origin).orElseThrow(NotFoundException::new);
	}

	@PreAuthorize("@auth.canWriteTag(#user.tag)")
	public void update(User user) {
		if (!user.local()) throw new ForeignWriteException();
		if (!userRepository.existsByTagAndOrigin(user.getTag(), user.getOrigin())) throw new NotFoundException();
		userRepository.save(user);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			userRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
