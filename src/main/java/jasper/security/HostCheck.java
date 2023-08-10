package jasper.security;

import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

@Component
public class HostCheck {
	private static final Logger logger = LoggerFactory.getLogger(HostCheck.class);

	@Autowired
	Props props;

	public boolean validHost(URI uri) {
		try {
			var host = InetAddress.getByName(uri.getHost());
			if (isNotEmpty(props.getScrapeHostWhitelist())) {
				if (!whitelisted(uri.getHost())) return false;
			} else {
				if (host.isLoopbackAddress()) return false;
				if (host.isMulticastAddress()) return false;
				if (host.isAnyLocalAddress()) return false;
				if (host.isSiteLocalAddress()) return false;
			}
			if (props.getScrapeHostBlacklist() != null) {
				for (var h : props.getScrapeHostBlacklist()) {
					if (uri.getHost().equals(h)) return false;
				}
			}
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	private boolean whitelisted(String host) {
		for (var h : props.getScrapeHostWhitelist()) {
			if (host.equals(h)) return true;
		}
		return false;
	}
}
