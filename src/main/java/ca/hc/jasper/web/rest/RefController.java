package ca.hc.jasper.web.rest;

import javax.validation.Valid;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.service.RefService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/ref")
public class RefController {

	@Autowired
	RefService refService;

	@PostMapping
	void createRef(
		@Valid @RequestBody Ref ref
	) {
		refService.create(ref);
	}

	@GetMapping
	Ref getRef(
		@RequestParam String url,
		@RequestParam(defaultValue = "") String origin
	) {
		return refService.get(url, origin);
	}

	@GetMapping("list")
	Page<Ref> getRefs(
		@PageableDefault(direction = Direction.DESC, sort = "created") Pageable pageable
	) {
		return refService.page(pageable);
	}

	@PutMapping
	void updateRef(
		@Valid @RequestBody Ref ref
	) {
		refService.update(ref);
	}

	@DeleteMapping
	void deleteRef(
		@RequestParam String url
	) {
		refService.delete(url);
	}
}
