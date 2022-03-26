package ca.hc.jasper.component;

import java.util.List;
import java.util.Optional;

import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class UserManager {

	@Autowired
	UserRepository userRepository;

	public UserDetails getPrincipal() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		return (UserDetails) authentication.getPrincipal();
	}

	public String getUserTag() {
		return getPrincipal().getUsername();
	}

	public Optional<User> getUser(String tag) {
		return userRepository.findOneByTagAndOrigin(tag, "");
	}

	public Optional<User> getUser() {
		return getUser(getUserTag());
	}

	public List<String> getReadAccess(String tag) {
		return getUser(tag).map(User::getReadAccess).orElse(null);
	}

	public List<String> getReadAccess() {
		return getReadAccess(getUserTag());
	}

	public List<String> getWriteAccess(String tag) {
		return getUser(tag).map(User::getWriteAccess).orElse(null);
	}

	public List<String> getWriteAccess() {
		return getWriteAccess(getUserTag());
	}
}
