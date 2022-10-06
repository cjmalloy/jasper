package jasper.service;

import jasper.component.Backup;
import jasper.errors.NotFoundException;
import jasper.security.Auth;
import jasper.service.dto.BackupOptionsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Profile("storage")
@Service
public class BackupService {

	@Autowired
	Backup backup;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.canBackup()")
	public String createBackup(BackupOptionsDto options) throws IOException {
		var id = Instant.now().toString();
		backup.createBackup(id, options);
		return id;
	}

	@PreAuthorize("@auth.canBackup()")
	public void uploadBackup(String id, byte[] zipFile) throws IOException {
		backup.store(id, zipFile);
	}

	@PreAuthorize("@auth.canReadBackup()")
	public List<String> listBackups() {
		return backup.listBackups();
	}

	@PreAuthorize("@auth.canReadBackup()")
	public byte[] getBackup(String id) {
		return backup.get(id);
	}

	@PreAuthorize("@auth.canBackup()")
	public void restoreBackup(String id, BackupOptionsDto options) {
		if (!backup.exists(id)) throw new NotFoundException("Backup " + id);
		backup.restore(id, options);
	}

	@PreAuthorize("@auth.canBackup()")
	public void deleteBackup(String id) throws IOException {
		if (!backup.exists(id)) return; // Delete is idempotent
		backup.delete(id);
	}
}
