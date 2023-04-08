package jasper.security.jwt;

import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;

public class AnonFilter extends AnonymousAuthenticationFilter {
	private final Logger logger = LoggerFactory.getLogger(AnonFilter.class);

	private final TokenProviderImplAnon tokenProvider;

	public AnonFilter(Props props) {
		super("key");
		tokenProvider = new TokenProviderImplAnon(props);
	}

	@Override
	protected Authentication createAuthentication(HttpServletRequest request) {
		var auth = tokenProvider.getAuthentication(null);
		logger.debug("Anon Auth: {}", auth.getAuthorities());
		return auth;
	}
}
