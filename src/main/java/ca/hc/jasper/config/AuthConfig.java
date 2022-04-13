package ca.hc.jasper.config;

import ca.hc.jasper.management.SecurityMetersService;
import ca.hc.jasper.security.jwt.*;
import org.springframework.context.annotation.*;

@Configuration
public class AuthConfig {

	@Bean
	@Profile({"jwt", "default"})
	TokenProvider tokenProvider(ApplicationProperties applicationProperties, SecurityMetersService securityMetersService) {
		return new TokenProviderImpl(applicationProperties, securityMetersService);
	}

	@Bean
	@Profile({"jwt-no-verify"})
	TokenProvider noVerifyTokenProvider(SecurityMetersService securityMetersService) {
		return new TokenProviderImplNoVerify(securityMetersService);
	}
}
