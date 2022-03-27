package ca.hc.jasper.service;

import ca.hc.jasper.security.Auth;
import ca.hc.jasper.domain.Queue;
import ca.hc.jasper.repository.QueueRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QueueService {

	@Autowired
	QueueRepository queueRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('MOD')")
	public void create(Queue queue) {
		if (!queue.local()) throw new ForeignWriteException();
		if (queueRepository.existsByTagAndOrigin(queue.getTag(), queue.getOrigin())) throw new AlreadyExistsException();
		queueRepository.save(queue);
	}

	@PostAuthorize("@auth.canReadTag(returnObject)")
	public Queue get(String tag, String origin) {
		return queueRepository.findOneByTagAndOrigin(tag, origin).orElseThrow(NotFoundException::new);
	}

	public Page<Queue> page(Pageable pageable) {
		return queueRepository.findAll(
			auth.queueReadSpec(),
			pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#queue.tag)")
	public void update(Queue queue) {
		if (!queue.local()) throw new ForeignWriteException();
		if (!queueRepository.existsByTagAndOrigin(queue.getTag(), queue.getOrigin())) throw new NotFoundException();
		queueRepository.save(queue);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			queueRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
