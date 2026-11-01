package jasper.web.rest.errors;

import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/exception-translator-test")
public class ExceptionTranslatorValidatedTestController {

	@GetMapping("/constraint-violation")
	public void constraintViolation(@RequestParam List<@Pattern(regexp = "[a-z]+") String> tags) {}
}
