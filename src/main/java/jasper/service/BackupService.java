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

	private static final BackupOptionsDto DEFAULT_OPTIONS = BackupOptionsDto.builder()
			.ref(true)
			.ref(true)
			.plugin(true)
			.template(true)
			.user(true)
			.build();

	@Autowired
	Backup backup;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.sysAdmin()")
	public String createBackup(BackupOptionsDto options) throws IOException {
		var id = Instant.now().toString();
		if (options == null) options = DEFAULT_OPTIONS;
		if (options.getNewerThan() != null) {
			id += "_-_" + options.getNewerThan();
		}
		backup.createBackup(id, options);
		return id;
	}

	@PreAuthorize("@auth.sysMod()")
	public void uploadBackup(String id, byte[] zipFile) throws IOException {
		backup.store(id, zipFile);
	}

	@PreAuthorize("@auth.sysMod()")
	public List<String> listBackups() {
		return backup.listBackups();
	}

	@PreAuthorize("@auth.sysMod()")
	public byte[] getBackup(String id) {
		return backup.get(id);
	}

	public byte[] getBackupPreauth(String id) {
		return backup.get(id);
	}

	@PreAuthorize("@auth.sysAdmin()")
	public void restoreBackup(String id, BackupOptionsDto options) {
		if (!backup.exists(id)) throw new NotFoundException("Backup " + id);
		backup.restore(id, options);
	}

	@PreAuthorize("@auth.sysAdmin()")
	public void deleteBackup(String id) throws IOException {
		if (!backup.exists(id)) return; // Delete is idempotent
		backup.delete(id);
	}
}
