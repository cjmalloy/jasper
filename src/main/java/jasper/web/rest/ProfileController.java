package jasper.web.rest;

import jasper.domain.User;
import jasper.service.ProfileService;
import jasper.service.dto.ProfileDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import static jasper.domain.TagId.QTAG_LEN;

@Profile("scim")
@RestController
@RequestMapping("api/v1/profile")
@Validated
public class ProfileController {
	@Autowired
	ProfileService profileService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createUser(
		@RequestBody @Valid ProfileDto profile
	) {
		profileService.create(profile.getTag(), profile.getPassword(), profile.getRole());
	}

	@GetMapping
	String[] getRoles(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = User.REGEX) String tag
	) {
		return profileService.getRoles(tag);
	}

	@GetMapping("page")
	Page<String> getPage(
		@PageableDefault Pageable pageable
	) {
		return profileService.getUsers(pageable.getPageNumber(), pageable.getPageSize());
	}

	@PostMapping("password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(
		@RequestBody @Valid ProfileDto profile
	) {
		profileService.changePassword(profile.getTag(), profile.getPassword());
	}

	@PostMapping("role")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changeRole(
		@RequestBody @Valid ProfileDto profile
	) {
		profileService.changeRole(profile.getTag(), profile.getRole());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteUser(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = User.REGEX) String tag
	) {
		profileService.delete(tag);
	}
}
