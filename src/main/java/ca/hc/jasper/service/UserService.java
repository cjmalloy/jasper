package ca.hc.jasper.service;

import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	@Autowired
	UserRepository userRepository;

	public void create(User user) {
		if (!user.local()) throw new ForeignWriteException();
		if (userRepository.existsByTagAndOrigin(user.getTag(), user.getOrigin())) throw new AlreadyExistsException();
		userRepository.save(user);
	}

	public User get(String tag, String origin) {
		return userRepository.findOneByTagAndOrigin(tag, origin).orElseThrow(NotFoundException::new);
	}

	public void update(User user) {
		if (!user.local()) throw new ForeignWriteException();
		if (!userRepository.existsByTagAndOrigin(user.getTag(), user.getOrigin())) throw new NotFoundException();
		userRepository.save(user);
	}

	public void delete(String tag) {
		try {
			userRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
