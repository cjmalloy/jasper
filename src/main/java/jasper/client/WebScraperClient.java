package jasper.client;

import feign.RequestLine;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;

import java.net.URI;

@FeignClient(value = "scrape")
public interface WebScraperClient {

	@RequestLine("GET")
	Response scrape(URI baseUri);
}
