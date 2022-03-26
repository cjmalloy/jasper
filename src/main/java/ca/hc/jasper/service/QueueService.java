package ca.hc.jasper.service;

import ca.hc.jasper.domain.Queue;
import ca.hc.jasper.repository.QueueRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

	@Autowired
	QueueRepository queueRepository;

	public void create(Queue queue) {
		if (!queue.local()) throw new ForeignWriteException();
		if (queueRepository.existsByTagAndOrigin(queue.getTag(), queue.getOrigin())) throw new AlreadyExistsException();
		queueRepository.save(queue);
	}

	public Queue get(String tag, String origin) {
		return queueRepository.findOneByTagAndOrigin(tag, origin).orElseThrow(NotFoundException::new);
	}

	public void update(Queue queue) {
		if (!queue.local()) throw new ForeignWriteException();
		if (!queueRepository.existsByTagAndOrigin(queue.getTag(), queue.getOrigin())) throw new NotFoundException();
		queueRepository.save(queue);
	}

	public void delete(String tag) {
		try {
			queueRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
