package jasper.security.jwt;

import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static jasper.security.Auth.LOCAL_ORIGIN_HEADER;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is
 * found.
 */
public class JWTFilter extends GenericFilterBean {
	private static final Logger logger = LoggerFactory.getLogger(JWTFilter.class);

	public static final String AUTHORIZATION_HEADER = "Authorization";

	private final Props props;
	private final TokenProvider tokenProvider;
	private final TokenProviderImplDefault defaultTokenProvider;

	public JWTFilter(Props props, TokenProvider tokenProvider, TokenProviderImplDefault defaultTokenProvider) {
		this.props = props;
		this.tokenProvider = tokenProvider;
		this.defaultTokenProvider = defaultTokenProvider;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		var httpServletRequest = (HttpServletRequest) servletRequest;
		if ("OPTIONS".equalsIgnoreCase(httpServletRequest.getMethod())) {
			SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken("options", null));
		}

		var origin = resolveOrigin(httpServletRequest);
		var jwt = resolveToken(httpServletRequest);
		if (tokenProvider.validateToken(jwt, origin)) {
			SecurityContextHolder.getContext().setAuthentication(tokenProvider.getAuthentication(jwt, origin));
		} else {
			SecurityContextHolder.getContext().setAuthentication(defaultTokenProvider.getAuthentication(null, origin));
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	private String resolveToken(HttpServletRequest request) {
		var bearerToken = request.getHeader(AUTHORIZATION_HEADER);
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}

	private String resolveOrigin(HttpServletRequest request) {
		var origin = props.getLocalOrigin();
		var headerOrigin = request.getHeader(LOCAL_ORIGIN_HEADER);
		if (props.isAllowLocalOriginHeader() && headerOrigin != null) {
			return headerOrigin.toLowerCase();
		}
		return origin;
	}
}
