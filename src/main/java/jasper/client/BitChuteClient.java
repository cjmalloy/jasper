package jasper.client;

import com.fasterxml.jackson.databind.JsonNode;
import feign.QueryMap;
import feign.RequestLine;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.Map;

@FeignClient(value = "bitChute", url = "https://www.bitchute.com/")
public interface BitChuteClient {

	@RequestLine("GET /oembed")
	JsonNode oembed(@QueryMap Map<String, String> params);
}
