package ca.hc.jasper.web.rest;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.service.RefService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/ref")
public class RefController {

	@Autowired
	RefService refService;

	@PostMapping
	void createRef(Ref ref) {
		refService.create(ref);
	}
}
