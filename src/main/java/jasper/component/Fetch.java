package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.errors.NotFoundException;
import jasper.errors.ScrapeProtocolException;
import jasper.repository.RefRepository;
import jasper.security.HostCheck;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Profile("proxy")
@Component
public class Fetch {
	private static final Logger logger = LoggerFactory.getLogger(Fetch.class);

	@Autowired
	HostCheck hostCheck;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Tagger tagger;

	@Autowired
	Optional<FileCache> fileCache;

	@Autowired
	Sanitizer sanitizer;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	CloseableHttpClient client;

	FileRequest doScrape(String url) throws IOException {
		if (!url.startsWith("http:") && !url.startsWith("https:")) throw new ScrapeProtocolException(url.contains(":") ? url.substring(0, url.indexOf(":")) : "unknown");
		return wrap(doWebScrape(url));
	}

	private CloseableHttpResponse doWebScrape(String url) throws IOException {
		HttpUriRequest request = new HttpGet(url);
		if (!hostCheck.validHost(request.getURI())) {
			logger.info("Invalid host {}", request.getURI().getHost());
			throw new NotFoundException("Invalid host.");
		}
		request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
		var res = client.execute(request);
		if (res.getStatusLine().getStatusCode() == 301 || res.getStatusLine().getStatusCode() == 304) {
			return doWebScrape(res.getFirstHeader("Location").getElements()[0].getValue());
		}
		return res;
	}

	private FileRequest wrap(CloseableHttpResponse res) {
		return new FileRequest() {
			@Override
			public String getMimeType() {
				return res.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return res.getEntity().getContent();
			}

			@Override
			public void close() throws IOException {
				EntityUtils.consumeQuietly(res.getEntity());
				res.close();
			}
		};
	}

	public interface FileRequest extends Closeable {
		String getMimeType();
		InputStream getInputStream() throws IOException;
	}

}
