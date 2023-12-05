package jasper.config;

import jasper.security.jwt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.context.annotation.ApplicationScope;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

import static jasper.security.AuthoritiesConstants.ADMIN;
import static jasper.security.AuthoritiesConstants.ANONYMOUS;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.security.AuthoritiesConstants.SA;
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.security.AuthoritiesConstants.VIEWER;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Import(SecurityProblemSupport.class)
public class SecurityConfiguration {
	private final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Autowired
    Props props;

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
		var unsafeSecret = profile("dev") && profile("jwt");
		if (!props.isDebug()) props.setDebug(unsafeSecret);
		if (props.isDebug()) {
			logger.error("==================================================");
			logger.error("==================================================\n");
			logger.error("DEBUG MODE\n");
			logger.error("==================================================");
			logger.error("==================================================");
		}
		if (props.isMultiTenant()) {
			logger.warn("==================================================");
			logger.warn("==================================================\n");
			logger.warn("MULTI TENANT\n");
			logger.warn("==================================================");
			logger.warn("==================================================");
		} else {
			logger.warn("==================================================");
			logger.warn("==================================================\n");
			logger.warn("SINGLE TENANT\n");
			logger.warn("==================================================");
			logger.warn("==================================================");
		}
	}

	private boolean profile(String profile) {
		return Arrays.asList(profiles).contains(profile);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .exceptionHandling()
                .authenticationEntryPoint(problemSupport)
                .accessDeniedHandler(problemSupport)
        .and()
            .headers()
            .contentSecurityPolicy(props.getSecurity().getContentSecurityPolicy())
        .and()
            .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
        .and()
            .permissionsPolicy().policy("camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")
        .and()
            .frameOptions()
            .deny()
        .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
			.apply(securityConfigurerAdapter())
		.and()
			.authorizeRequests()
			.antMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
		.and()
			.headers()
			.frameOptions()
			.sameOrigin()
		.and()
			.csrf()
			.csrfTokenRepository(csrfTokenRepository())
			.ignoringAntMatchers("/pub/api/**") // Public API
		; // @formatter:on
		return http.build();
	}

	@Bean
	JWTConfigurer securityConfigurerAdapter() {
		logger.info("Minimum Role: {}", props.getMinRole());
		return new JWTConfigurer(props, tokenProvider, defaultTokenProvider);
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		var r = CookieCsrfTokenRepository.withHttpOnlyFalse();
		r.setSecure(false); // Required when using SSL terminating gateway
		return r;
	}

	@Bean
	@ApplicationScope
	public RoleHierarchy roleHierarchy() {
		RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
		String hierarchy = String.join("\n", List.of(
			SA + " > " + ADMIN,
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
