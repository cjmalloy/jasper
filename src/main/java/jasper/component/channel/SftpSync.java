package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Constantly sync cache files over SFTP for remote origins with
 * the tunnel SFTP mode set to sync.
 */
@Profile("file-cache")
@Component
public class SftpSync {
	private static final Logger logger = LoggerFactory.getLogger(SftpSync.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Replicator replicator;

	@Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void sync() {
		var root = configs.root();
		for (var origin : root.scriptOrigins("+plugin/origin/pull")) {
			if (!root.script("+plugin/origin/pull", origin)) continue;
			var remotes = refRepository.findAll(RefFilter.builder()
				.origin(origin)
				.query("+plugin/origin/pull:+plugin/origin/tunnel:!+plugin/error:!plugin/delete")
				.build().spec());
			for (var remote : remotes) {
				logger.trace("{} Syncing cache over SFTP {}: {}", origin, remote.getTitle(), remote.getUrl());
				replicator.sftpSync(remote);
			}
		}
	}
}
