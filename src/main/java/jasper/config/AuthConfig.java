package jasper.config;

import jasper.management.SecurityMetersService;
import jasper.security.jwt.*;
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
