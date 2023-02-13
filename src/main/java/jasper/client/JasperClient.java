package jasper.client;

import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import org.springframework.cloud.openfeign.FeignClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@FeignClient(value = "jasper", url = "https://jasperkms.info")
public interface JasperClient {

	@RequestLine("GET /api/v1/repl/ref")
	List<Ref> refPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/ref/cursor/{origin}")
	Instant refCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("POST /api/v1/repl/ref")
	void refPush(URI baseUri, List<Ref> push);

	@RequestLine("GET /api/v1/repl/ext")
	List<Ext> extPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/ext/cursor/{origin}")
	Instant extCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("GET /api/v1/repl/ext")
	void extPush(URI baseUri, List<Ext> push);

	@RequestLine("GET /api/v1/repl/user")
	List<User> userPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/user/cursor/{origin}")
	Instant userCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("GET /api/v1/repl/user")
	void userPush(URI baseUri, List<User> push);

	@RequestLine("GET /api/v1/repl/plugin")
	List<Plugin> pluginPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/plugin/cursor/{origin}")
	Instant pluginCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("GET /api/v1/repl/plugin")
	void pluginPush(URI baseUri, List<Plugin> push);

	@RequestLine("GET /api/v1/repl/template")
	List<Template> templatePull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/template/cursor/{origin}")
	Instant templateCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("GET /api/v1/repl/template")
	void templatePush(URI baseUri, List<Template> push);
}
