package jasper.web.rest;

import jasper.domain.User;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.service.UserService;
import jasper.service.dto.RolesDto;
import jasper.service.dto.UserDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import java.time.Instant;

import static jasper.domain.TagId.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;
import static jasper.util.RestUtil.ifModifiedSincePage;
import static jasper.util.RestUtil.sortedByTime;

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
	HttpEntity<UserDto> getUser(
		WebRequest request,
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = User.REGEX) String tag
	) {
		return ifModifiedSince(request, userService.get(tag));
	}

	@GetMapping("page")
	HttpEntity<Page<UserDto>> getPage(
		WebRequest request,
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		var result = userService.page(
			TagFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
		if (!sortedByTime(pageable)) return ResponseEntity.ok(result);
		return ifModifiedSincePage(request, result);
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
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = User.REGEX) String tag
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
