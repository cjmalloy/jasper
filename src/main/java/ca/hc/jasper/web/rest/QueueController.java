package ca.hc.jasper.web.rest;

import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Queue;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.repository.filter.TagList;
import ca.hc.jasper.service.QueueService;
import ca.hc.jasper.service.dto.QueueDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/queue")
@Validated
public class QueueController {

	@Autowired
	QueueService queueService;

	@PostMapping
	void createQueue(
		@RequestBody Queue queue
	) {
		queueService.create(queue);
	}

	@GetMapping
	QueueDto getQueue(
		@RequestParam String tag,
		@RequestParam(defaultValue = "") String origin
	) {
		return queueService.get(tag, origin);
	}

	@GetMapping("list")
	Page<QueueDto> getQueues(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query
	) {
		return queueService.page(
			TagFilter.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	void updateQueue(
		@RequestBody Queue queue
	) {
		queueService.update(queue);
	}

	@DeleteMapping
	void deleteQueue(
		@RequestParam String tag
	) {
		queueService.delete(tag);
	}
}
