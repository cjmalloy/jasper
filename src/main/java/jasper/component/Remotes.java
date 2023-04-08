package jasper.component;

import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Remotes {
	private static final Logger logger = LoggerFactory.getLogger(Remotes.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Replicator replicator;

	/**
	 * Pull from a remote.
	 * @param origin to search for out of date remotes
	 * @return <code>true</code> if a remote was pulled
	 */
	public boolean pull(String origin) {
		var maybePull = refRepository.oldestNeedsPullByOrigin(origin);
		if (maybePull.isEmpty()) return false;
		replicator.pull(maybePull.get());
		return true;
	}

	/**
	 * Push from a remote.
	 * @param origin to search for out of date remotes
	 * @return <code>true</code> if a remote was pushed
	 */
	public boolean push(String origin) {
		var maybePush = refRepository.oldestNeedsPushByOrigin(origin);
		if (maybePush.isEmpty()) return false;
		replicator.push(maybePush.get());
		return true;
	}

}
