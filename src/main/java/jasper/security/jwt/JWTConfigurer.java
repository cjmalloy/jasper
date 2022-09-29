package jasper.security.jwt;

import jasper.config.Props;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JWTConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    private final TokenProvider tokenProvider;
    private final Props props;

    public JWTConfigurer(TokenProvider tokenProvider, Props props) {
        this.tokenProvider = tokenProvider;
        this.props = props;
    }

    @Override
    public void configure(HttpSecurity http) {
        JWTFilter customFilter = new JWTFilter(tokenProvider, props);
        http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
