package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.component.HttpCache;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.service.GraphService;
import jasper.service.dto.RefNodeDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

import static jasper.domain.Ref.URL_LEN;

@RestController
@RequestMapping("api/v1/graph")
@Validated
@Tag(name = "Graph")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class GraphController {

	@Autowired
	GraphService graphService;

	@Autowired
	HttpCache httpCache;

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("list")
	HttpEntity<List<RefNodeDto>> getGraphList(
		WebRequest request,
		@RequestParam @Size(max = 100) List<@Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String> urls
	) {
		return httpCache.ifNotModifiedList(request, urls.stream().map(url -> {
			try {
				return graphService.get(url);
			} catch (NotFoundException | AccessDeniedException e) {
				return null;
			}
		}).toList());
	}
}
