package jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import jasper.domain.User;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.service.UserService;
import jasper.service.dto.RolesDto;
import jasper.service.dto.UserDto;
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
		@RequestParam @Pattern(regexp = User.REGEX) String tag
	) {
		return userService.get(tag);
	}

	@GetMapping("page")
	Page<UserDto> getPage(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return userService.page(
			TagFilter
				.builder()
				.modifiedAfter(modifiedAfter)
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
		@RequestParam @Pattern(regexp = User.REGEX) String tag
	) {
		userService.delete(tag);
	}

	@GetMapping("whoami")
	RolesDto whoAmI() {
		return RolesDto
			.builder()
			.tag(auth.getUserTag())
			.admin(auth.hasRole("ADMIN"))
			.mod(auth.hasRole("MOD"))
			.editor(auth.hasRole("EDITOR"))
			.build();
	}
}
