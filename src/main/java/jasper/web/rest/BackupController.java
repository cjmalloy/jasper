package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.errors.NotFoundException;
import jasper.service.BackupService;
import jasper.service.dto.BackupOptionsDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static jasper.service.dto.BackupOptionsDto.ID_LEN;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("storage")
@RestController
@RequestMapping("api/v1/backup")
@Tag(name = "Backup")
@ApiResponses({
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class BackupController {

	@Autowired
	BackupService backupService;

	// TODO: add clustered option
	private static String backupKey;
	private static Instant backupKeyCreated;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
	})
	@PostMapping
	public String createBackup(
		@RequestBody(required = false) BackupOptionsDto options
	) throws IOException {
		return backupService.createBackup(options);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping
	public List<String> listBackups() {
		return backupService.listBackups();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping(value = "{id}")
	public ResponseEntity<byte[]> downloadBackup(
		@PathVariable @Length(max = ID_LEN) String id,
		@RequestParam(defaultValue = "") String p
	) {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		byte[] file;
		if (isBlank(p)) {
			file = backupService.getBackup(id);
		} else {
			if (!p.equals(backupKey)) throw new NotFoundException(id);
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
		if (key.equals(backupKey)) return key;
		backupKeyCreated = Instant.now();
		return backupKey = UUID.randomUUID().toString();
	}

	@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
	public void clearBackupKey() {
		if (backupKey == null) return;
		if (backupKeyCreated != null && backupKeyCreated.isAfter(Instant.now().minus(15, ChronoUnit.SECONDS))) return;
		backupKey = null;
		backupKeyCreated = null;
	}

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
	})
	@PostMapping("upload/{id}")
	@ResponseStatus(HttpStatus.CREATED)
	public void uploadBackup(
		@PathVariable @Length(max = ID_LEN) String id,
		@RequestBody(required = false) byte[] zipFile
	) throws IOException {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.uploadBackup(id, zipFile);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping("restore/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restoreBackup(
		@PathVariable @Length(max = ID_LEN) String id,
		@RequestBody(required = false) BackupOptionsDto options) {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.restoreBackup(id, options);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("backfill")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void backfill() {
		backupService.backfill();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping("{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteBackup(
		@PathVariable @Length(max = ID_LEN) String id
	) throws IOException {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.deleteBackup(id);
	}
}
