package jasper.component;

import jasper.errors.NotFoundException;
import jasper.errors.ScrapeProtocolException;
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

import java.io.IOException;
import java.io.InputStream;

import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.plugin.Pull.getPull;

@Profile("proxy")
@Component
public class FetchImplHttp implements Fetch {
	private static final Logger logger = LoggerFactory.getLogger(FetchImplHttp.class);

	@Autowired
	HostCheck hostCheck;

	@Autowired
	ConfigCache configs;

	@Autowired
	CloseableHttpClient http;

	@Autowired
	Replicator replicator;

	public FileRequest doScrape(String url, String origin) throws IOException {
		var remote = configs.getRemote(origin);
		var pull = getPull(remote);
		if (pull != null && (url.startsWith("cache:") || pull.isCacheProxy())) {
			if (hasMatchingTag(remote, "+plugin/error")) throw new ScrapeProtocolException("cache");
			return replicator.fetch(url, remote);
		}
		if (url.startsWith("http:") || url.startsWith("https:")) {
			return wrap(doWebScrape(url));
		}
		throw new ScrapeProtocolException(url.contains(":") ? url.substring(0, url.indexOf(":")) : "unknown");
	}

	private CloseableHttpResponse doWebScrape(String url) throws IOException {
		HttpUriRequest request = new HttpGet(url);
		if (!hostCheck.validHost(request.getURI())) {
			logger.info("Invalid host {}", request.getURI().getHost());
			throw new NotFoundException("Invalid host.");
		}
		request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
		var res = http.execute(request);
		if (res == null) return null;
		if (res.getStatusLine().getStatusCode() == 301 || res.getStatusLine().getStatusCode() == 304) {
			return doWebScrape(res.getFirstHeader("Location").getElements()[0].getValue());
		}
		return res;
	}

	private FileRequest wrap(CloseableHttpResponse res) {
		if (res == null) return null;
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

}
