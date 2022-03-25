package ca.hc.jasper.service;

import java.util.List;

import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	@Autowired
	UserRepository userRepository;

	public void create(User user) {
		if (!user.local()) throw new ForeignWriteException();
		if (userRepository.existsById(user.getId())) throw new AlreadyExistsException();
		userRepository.save(user);
	}

	public User get(TagId id) {
		return userRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public List<User> getAll(String tag) {
		return userRepository.findAllByTag(tag);
	}

	public void update(User user) {
		if (!user.local()) throw new ForeignWriteException();
		if (!userRepository.existsById(user.getId())) throw new NotFoundException();
		userRepository.save(user);
	}

	public void delete(String url) {
		userRepository.deleteById(new TagId(url, ""));
	}
}
