package ca.hc.jasper.service;

import java.util.List;

import ca.hc.jasper.domain.Queue;
import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.repository.QueueRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

	@Autowired
	QueueRepository queueRepository;

	public void create(Queue queue) {
		if (queue.getOrigin() != null) throw new ForeignWriteException();
		if (queueRepository.existsById(queue.getId())) throw new AlreadyExistsException();
		queueRepository.save(queue);
	}

	public Queue get(TagId id) {
		return queueRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public List<Queue> getAll(String tag) {
		return queueRepository.findAllByTag(tag);
	}

	public void update(Queue queue) {
		if (queue.getOrigin() != null) throw new ForeignWriteException();
		if (!queueRepository.existsById(queue.getId())) throw new NotFoundException();
		queueRepository.save(queue);
	}

	public void delete(String url) {
		queueRepository.deleteById(new TagId(url, null));
	}
}
