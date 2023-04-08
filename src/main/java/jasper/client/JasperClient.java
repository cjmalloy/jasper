package jasper.client;

import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import jasper.client.dto.RefDto;
import jasper.client.dto.UserDto;
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
	@RequestLine("GET /api/v1/repl/ref/cursor?origin={origin}")
	Instant refCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("POST /api/v1/repl/ref?origin={origin}")
	void refPush(URI baseUri, @Param("origin") String origin, List<RefDto> push);

	@RequestLine("GET /api/v1/repl/ext")
	List<Ext> extPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/ext/cursor?origin={origin}")
	Instant extCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("POST /api/v1/repl/ext?origin={origin}")
	void extPush(URI baseUri, @Param("origin") String origin, List<Ext> push);

	@RequestLine("GET /api/v1/repl/user")
	List<User> userPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/user/cursor?origin={origin}")
	Instant userCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("POST /api/v1/repl/user?origin={origin}")
	void userPush(URI baseUri, @Param("origin") String origin, List<UserDto> push);

	@RequestLine("GET /api/v1/repl/plugin")
	List<Plugin> pluginPull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/plugin/cursor?origin={origin}")
	Instant pluginCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("POST /api/v1/repl/plugin?origin={origin}")
	void pluginPush(URI baseUri, @Param("origin") String origin, List<Plugin> push);

	@RequestLine("GET /api/v1/repl/template")
	List<Template> templatePull(URI baseUri, @QueryMap Map<String, Object> params);
	@RequestLine("GET /api/v1/repl/template/cursor?origin={origin}")
	Instant templateCursor(URI baseUri, @Param("origin") String origin);
	@RequestLine("POST /api/v1/repl/template?origin={origin}")
	void templatePush(URI baseUri, @Param("origin") String origin, List<Template> push);
}
