package ca.hc.jasper.config;

import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.UserRepository;
import ca.hc.jasper.security.method.CustomMethodSecurityExpressionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {

	@Autowired
	UserRepository userRepository;
	@Autowired
	RefRepository refRepository;

	@Override
	protected MethodSecurityExpressionHandler createExpressionHandler() {
		return new CustomMethodSecurityExpressionHandler(userRepository, refRepository);
	}
}
