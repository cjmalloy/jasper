package jasper.service;

import jasper.component.Backup;
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
@PreAuthorize("hasRole('ADMIN')")
public class BackupService {

	@Autowired
	Backup backup;

	public String createBackup(BackupOptionsDto options) throws IOException {
		var id = Instant.now().toString();
		backup.createBackup(id, options);
		return id;
	}

	public void uploadBackup(String id, byte[] zipFile) throws IOException {
		backup.store(id, zipFile);
	}

	public List<String> listBackups() {
		return backup.listBackups();
	}

	public byte[] getBackup(String id) throws IOException {
		return backup.get(id);
	}

	public void restoreBackup(String id, BackupOptionsDto options) {
		backup.restore(id, options);
	}

	public void deleteBackup(String id) throws IOException {
		backup.delete(id);
	}
}
