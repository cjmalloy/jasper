package jasper.client;

import feign.QueryMap;
import feign.RequestLine;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import org.springframework.cloud.openfeign.FeignClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

@FeignClient(value = "jasper", url = "https://jasperkms.info")
public interface JasperClient {

	@RequestLine("GET /api/v1/repl/ref")
	List<Ref> ref(URI baseUri, @QueryMap Map<String, Object> params);

	@RequestLine("GET /api/v1/repl/ext")
	List<Ext> ext(URI baseUri, @QueryMap Map<String, Object> params);

	@RequestLine("GET /api/v1/repl/user")
	List<User> user(URI baseUri, @QueryMap Map<String, Object> params);

	@RequestLine("GET /api/v1/repl/plugin")
	List<Plugin> plugin(URI baseUri, @QueryMap Map<String, Object> params);

	@RequestLine("GET /api/v1/repl/template")
	List<Template> template(URI baseUri, @QueryMap Map<String, Object> params);
}
