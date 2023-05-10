package jasper.client;

import feign.RequestLine;
import org.springframework.cloud.openfeign.FeignClient;

import java.net.URI;

@FeignClient(value = "scrape")
public interface WebScraperClient {

	@RequestLine("GET")
	byte[] scrape(URI baseUri);
}
