package jasper.config;

import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.security.jwt.JWTConfigurer;
import jasper.security.jwt.TokenProvider;
import jasper.security.jwt.TokenProviderImplDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.context.annotation.ApplicationScope;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import java.util.Arrays;
import java.util.List;

import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.ANONYMOUS;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.security.AuthoritiesConstants.VIEWER;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(SecurityProblemSupport.class)
public class SecurityConfiguration {
	private final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Autowired
	Props props;
	@Autowired
	ConfigCache configs;
	@Autowired
	SecurityProblemSupport problemSupport;
	@Autowired
	TokenProvider tokenProvider;

	@Autowired
	TokenProviderImplDefault defaultTokenProvider;

	@Value("${spring.profiles.active}")
	String[] profiles;

	@PostConstruct
	void init() {
		var unsafeSecret = profile("dev") && isNotBlank(props.getOverride().getSecurity().getSecret());
		if (!props.isDebug()) props.setDebug(unsafeSecret);
		if (props.isDebug()) {
			logger.error("==================================================");
			logger.error("==================================================");
			logger.error("DEBUG MODE");
			logger.error("==================================================");
			logger.error("==================================================");
		}
	}

	private boolean profile(String profile) {
		return Arrays.asList(profiles).contains(profile);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .exceptionHandling(e -> e
                .authenticationEntryPoint(problemSupport)
                .accessDeniedHandler(problemSupport)
			)
            .headers(h -> h
				.contentSecurityPolicy(csp -> csp
					.policyDirectives(props.getSecurity().getContentSecurityPolicy()))
				.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.frameOptions(f -> f.sameOrigin())
				.permissionsPolicy(p -> p.policy("camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"))
			)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.apply(securityConfigurerAdapter())
		.and()
			.authorizeHttpRequests(r -> r
				.requestMatchers("/api/**").permitAll()
			)
			.csrf(c -> c
				.csrfTokenRequestHandler(csrfRequestHandler())
				.csrfTokenRepository(csrfTokenRepository())
				.ignoringRequestMatchers("/pub/api/**") // Public API
			)
		; // @formatter:on
		return http.build();
	}

	@Bean
	public AuthenticationManager noopAuthenticationManager() {
		return authentication -> {
			throw new AuthenticationServiceException("AuthenticationManager is disabled");
		};
	}

	@Bean
	JWTConfigurer securityConfigurerAdapter() {
		logger.info("Maximum Role: {}", props.getMaxRole());
		logger.info("Minimum Role: {}", props.getMinRole());
		return new JWTConfigurer(props, tokenProvider, defaultTokenProvider, configs);
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		var r = CookieCsrfTokenRepository.withHttpOnlyFalse();
		r.setSecure(false); // Required when using SSL terminating gateway
		return r;
	}

	@Bean
	CsrfTokenRequestAttributeHandler csrfRequestHandler() {
		CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
		// TODO: CSRF BREACH: https://docs.spring.io/spring-security/reference/5.8/migration/servlet/exploits.html
		// Opt out of deferred csrf token loading
		requestHandler.setCsrfRequestAttributeName(null);
		return requestHandler;
	}

	@Bean
	@ApplicationScope
	public RoleHierarchy roleHierarchy() {
		RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
		String hierarchy = String.join("\n", List.of(
			ADMIN + " > " + MOD,
			MOD + " > " + EDITOR,
			EDITOR + " > " + USER,
			USER + " > " + VIEWER,
			VIEWER + " > " + ANONYMOUS
		));
		roleHierarchy.setHierarchy(hierarchy);
		return roleHierarchy;
	}

	@Bean
	public DefaultWebSecurityExpressionHandler webSecurityExpressionHandler() {
		DefaultWebSecurityExpressionHandler expressionHandler = new DefaultWebSecurityExpressionHandler();
		expressionHandler.setRoleHierarchy(roleHierarchy());
		return expressionHandler;
	}
}
