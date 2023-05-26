package jasper.client;

import feign.Headers;
import feign.RequestLine;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;

import java.net.URI;

@FeignClient(value = "scrape")
public interface WebScraperClient {

	@RequestLine("GET")
	@Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
	Response scrape(URI baseUri);
}
