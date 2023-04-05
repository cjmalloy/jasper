package jasper.security.jwt;

import jasper.config.Props;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;

public class AnonFilter extends AnonymousAuthenticationFilter {

	private final TokenProviderImplAnon tokenProvider;

	public AnonFilter(Props props) {
		super("key");
		tokenProvider = new TokenProviderImplAnon(props);
	}

	@Override
	protected Authentication createAuthentication(HttpServletRequest request) {
		return tokenProvider.getAuthentication(null);
	}
}
