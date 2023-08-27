package jasper.security.jwt;

import jasper.config.Props;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JWTConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

	private final TokenProvider tokenProvider;
	private final TokenProviderImplDefault defaultTokenProvider;

	public JWTConfigurer(TokenProvider tokenProvider, TokenProviderImplDefault defaultTokenProvider) {
		this.tokenProvider = tokenProvider;
		this.defaultTokenProvider = defaultTokenProvider;
	}

	@Override
	public void configure(HttpSecurity http) {
		http.addFilterBefore(new JWTFilter(tokenProvider, defaultTokenProvider), UsernamePasswordAuthenticationFilter.class);
	}
}
