package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.User;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.service.UserService;
import jasper.service.dto.RolesDto;
import jasper.service.dto.UserDto;
import org.hibernate.validator.constraints.Length;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
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

import static jasper.domain.proj.Tag.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.repository.filter.Query.SEARCH_LEN;
import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.util.RestUtil.ifNotModified;
import static jasper.util.RestUtil.ifNotModifiedPage;

@RestController
@RequestMapping("api/v1/user")
@Validated
@Tag(name = "User")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class UserController {

	@Autowired
	UserService userService;

	@Autowired
	Auth auth;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createUser(
		@RequestBody @Valid User user
	) {
		userService.create(user);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	HttpEntity<UserDto> getUser(
		WebRequest request,
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = User.QTAG_REGEX) String tag
	) {
		return ifNotModified(request, userService.get(tag));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
	})
	@GetMapping("page")
	HttpEntity<Page<UserDto>> getUserPage(
		WebRequest request,
		@PageableDefault(sort = "tag") @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		return ifNotModifiedPage(request, userService.page(
			TagFilter.builder()
				.modifiedAfter(modifiedAfter)
				.search(search)
				.query(query).build(),
			pageable));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateUser(
		@RequestBody @Valid User user
	) {
		userService.update(user);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteUser(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = User.QTAG_REGEX) String tag
	) {
		userService.delete(tag);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("whoami")
	RolesDto whoAmI() {
		return RolesDto
			.builder()
			.tag(auth.getUserTag().toString())
			.admin(auth.hasRole(ADMIN))
			.mod(auth.hasRole(MOD))
			.editor(auth.hasRole(EDITOR))
			.user(auth.hasRole(USER))
			.build();
	}
}
