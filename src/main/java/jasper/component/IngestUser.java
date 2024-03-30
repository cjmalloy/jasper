package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.core.annotation.Timed;
import jasper.domain.User;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.config.Config.ServerConfig;
import jasper.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;

import static jasper.component.Replicator.deletedTag;
import static jasper.component.Replicator.deletorTag;
import static jasper.component.Replicator.isDeletorTag;

@Component
public class IngestUser {
	private static final Logger logger = LoggerFactory.getLogger(IngestUser.class);

	@Autowired
	UserRepository userRepository;

	@Autowired
	EntityManager em;

	@Autowired
	Messages messages;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	ConfigCache configs;

	ServerConfig root() {
		return configs.getTemplate("_config/server", "",  ServerConfig.class);
	}

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.user", histogram = true)
	public void create(User user) {
		if (isDeletorTag(user.getTag())) {
			if (userRepository.existsByQualifiedTag(deletedTag(user.getTag()) + user.getOrigin())) throw new AlreadyExistsException();
		} else {
			delete(deletorTag(user.getTag()) + user.getOrigin());
		}
		ensureCreateUniqueModified(user);
		messages.updateUser(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void update(User user) {
		ensureUpdateUniqueModified(user);
		messages.updateUser(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void push(User user) {
		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
		if (isDeletorTag(user.getTag())) {
			delete(deletedTag(user.getTag()) + user.getOrigin());
		}
		messages.updateUser(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void delete(String qualifiedTag) {
		userRepository.deleteByQualifiedTag(qualifiedTag);
	}

	void ensureCreateUniqueModified(User user) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					user.setModified(Instant.now(ensureUniqueModifiedClock));
					em.persist(user);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("users_pkey".equals(c.getConstraintName())) throw new AlreadyExistsException();
					if ("users_modified_origin_key".equals(c.getConstraintName())) {
						if (count > root().getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

	void ensureUpdateUniqueModified(User user) {
		var cursor = user.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					user.setModified(Instant.now(ensureUniqueModifiedClock));
					var updated = userRepository.optimisticUpdate(
						cursor,
						user.getTag(),
						user.getOrigin(),
						user.getName(),
						user.getRole(),
						user.getReadAccess() == null ? null : objectMapper.convertValue(user.getReadAccess(), ArrayNode.class),
						user.getWriteAccess() == null ? null : objectMapper.convertValue(user.getWriteAccess(), ArrayNode.class),
						user.getTagReadAccess() == null ? null : objectMapper.convertValue(user.getTagReadAccess(), ArrayNode.class),
						user.getTagWriteAccess() == null ? null : objectMapper.convertValue(user.getTagWriteAccess(), ArrayNode.class),
						user.getModified(),
						user.getKey(),
						user.getPubKey());
					if (updated == 0) {
						throw new ModifiedException("User");
					}
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("users_modified_origin_key".equals(c.getConstraintName())) {
						if (count > root().getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

}
