package jasper.security.jwt;

import jasper.config.Props;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JWTConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

	private final Props props;
	private final TokenProvider tokenProvider;

	public JWTConfigurer(Props props, TokenProvider tokenProvider) {
		this.props = props;
		this.tokenProvider = tokenProvider;
	}

	@Override
	public void configure(HttpSecurity http) {
		http.addFilterBefore(new JWTFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
		http.addFilterBefore(new AnonFilter(props), UsernamePasswordAuthenticationFilter.class);
	}
}
