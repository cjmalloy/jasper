package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jasper.domain.proj.HasOrigin;
import jasper.errors.NotFoundException;
import jasper.service.BackupService;
import jasper.service.dto.BackupOptionsDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static jasper.service.dto.BackupOptionsDto.ID_LEN;
import static org.apache.commons.lang3.StringUtils.isBlank;

@RestController
@RequestMapping("api/v1/backup")
@Tag(name = "Backup")
@ApiResponses({
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class BackupController {

	@Autowired
	BackupService backupService;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
	})
	@PostMapping
	public String createBackup(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestBody(required = false) BackupOptionsDto options
	) throws IOException {
		return backupService.createBackup(origin, options);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping
	public List<String> listBackups(@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin) {
		return backupService.listBackups(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping(value = "{id}")
	public ResponseEntity<byte[]> downloadBackup(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@PathVariable @Length(max = ID_LEN) String id,
		@RequestParam(defaultValue = "") String p
	) {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		byte[] file;
		if (isBlank(p)) {
			file = backupService.getBackup(origin, id);
		} else {
			if (!backupService.unlock(p)) throw new NotFoundException(id);
			file = backupService.getBackupPreauth(id);
		}
		return ResponseEntity.ok()
			.contentType(MediaType.valueOf("application/zip"))
			.header("Content-Disposition", "attachment")
			.body(file);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping(value = "key")
	public String getBackupKey(
		@RequestParam(defaultValue = "") String key
	) {
		return backupService.getKey(key);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
	})
	@PostMapping("upload/{id}")
	@ResponseStatus(HttpStatus.CREATED)
	public void uploadBackup(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@PathVariable @Length(max = ID_LEN) String id,
		InputStream zipFile
	) throws IOException {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.uploadBackup(origin, id, zipFile);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping("restore/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restoreBackup(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@PathVariable @Length(max = ID_LEN) String id,
		@RequestBody(required = false) BackupOptionsDto options
	) {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.restoreBackup(origin, id, options);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("regen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void backfill(@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin) {
		backupService.regen(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping("{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteBackup(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@PathVariable @Length(max = ID_LEN) String id
	) throws IOException {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.deleteBackup(origin, id);
	}
}
