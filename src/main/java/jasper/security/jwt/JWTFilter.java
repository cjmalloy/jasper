package jasper.security.jwt;

import jasper.config.ApplicationProperties;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is
 * found.
 */
public class JWTFilter extends GenericFilterBean {
	private static final Logger logger = LoggerFactory.getLogger(JWTFilter.class);

	public static final String AUTHORIZATION_HEADER = "Authorization";

	private final TokenProvider tokenProvider;
	private final ApplicationProperties applicationProperties;

	public JWTFilter(TokenProvider tokenProvider, ApplicationProperties applicationProperties) {
		this.tokenProvider = tokenProvider;
		this.applicationProperties = applicationProperties;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
		throws IOException, ServletException {
		HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
		String jwt = resolveToken(httpServletRequest);
		String origin = applicationProperties.getDefaultOrigin();
		if (applicationProperties.isAllowAuthHeaders()) {
			var originHeader = resolveOrigin(httpServletRequest);
			if (originHeader != null) {
				origin = originHeader;
			}
		}
		if (tokenProvider.validateToken(jwt)) {
			Authentication authentication = tokenProvider.getAuthentication(jwt, origin);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	private String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}

	private String resolveOrigin(HttpServletRequest request) {
		String origin = request.getHeader(Auth.ORIGIN_HEADER);
		if (origin != null) {
			return origin.toLowerCase();
		}
		return null;
	}
}
