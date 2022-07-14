package jasper.web.rest;

import jasper.service.BackupService;
import jasper.service.dto.BackupOptionsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Profile("storage")
@RestController
@RequestMapping("api/v1/backup")
public class BackupController {

	@Autowired
	BackupService backupService;

	@PostMapping
	public String create(@RequestBody(required = false) BackupOptionsDto options) throws IOException {
		return backupService.createBackup(options);
	}

	@GetMapping
	public List<String> list() {
		return backupService.listBackups();
	}

	@GetMapping(value = "{id}")
	public ResponseEntity<byte[]> download(@PathVariable String id) throws IOException {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		return ResponseEntity.ok()
			.contentType(MediaType.valueOf("application/zip"))
			.header("Content-Disposition", "attachment")
			.body(backupService.getBackup(id));
	}

	@PostMapping("upload")
	public String upload(@RequestBody(required = false) byte[] zipFile) throws IOException {
		return backupService.uploadBackup(zipFile);
	}

	@PostMapping("restore/{id}")
	public void restore(@PathVariable String id,
						@RequestBody(required = false) BackupOptionsDto options) {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.restoreBackup(id, options);
	}

	@DeleteMapping("{id}")
	public void delete(@PathVariable String id) throws IOException {
		if (id.endsWith(".zip")) {
			id = id.substring(0, id.length() - 4);
		}
		backupService.deleteBackup(id);
	}
}
