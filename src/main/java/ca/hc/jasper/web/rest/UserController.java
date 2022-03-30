package ca.hc.jasper.web.rest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.User;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.repository.filter.TagList;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.UserService;
import ca.hc.jasper.service.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/user")
@Validated
public class UserController {

	@Autowired
	UserService userService;

	@Autowired
	Auth auth;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createUser(
		@RequestBody @Valid User user
	) {
		userService.create(user);
	}

	@GetMapping
	UserDto getUser(
		@RequestParam @Pattern(regexp = Tag.REGEX) String tag,
		@RequestParam(defaultValue = "") String origin
	) {
		return userService.get(tag, origin);
	}

	@GetMapping("list")
	Page<UserDto> getUsers(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query
	) {
		return userService.page(
			TagFilter.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateUser(
		@RequestBody @Valid User user
	) {
		userService.update(user);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteUser(
		@RequestParam @Pattern(regexp = Tag.REGEX) String tag
	) {
		userService.delete(tag);
	}

	@DeleteMapping("notifications")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clearNotifications(
		@RequestParam @Pattern(regexp = Tag.REGEX) String tag
	) {
		userService.clearNotifications(tag);
	}

	@GetMapping("whoami")
	String whoAmI() {
		return auth.getUserTag();
	}
}