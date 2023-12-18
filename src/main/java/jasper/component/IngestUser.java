package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.User;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

@Component
public class IngestUser {
	private static final Logger logger = LoggerFactory.getLogger(IngestUser.class);

	@Autowired
	Props props;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EntityManager em;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.user", histogram = true)
	public void create(User user) {
		ensureCreateUniqueModified(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void update(User user) {
		ensureUpdateUniqueModified(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void push(User user) {
		userRepository.save(user);
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
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
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
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

}
