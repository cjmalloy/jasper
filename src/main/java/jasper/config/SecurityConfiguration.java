package jasper.config;

import jasper.component.ConfigCache;
import jasper.security.jwt.JWTConfigurer;
import jasper.security.jwt.TokenProvider;
import jasper.security.jwt.TokenProviderImplDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
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
import static jasper.security.AuthoritiesConstants.USER;
import static jasper.security.AuthoritiesConstants.VIEWER;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
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
		logger.warn("==================================================");
		logger.warn("==================================================");
		logger.warn("DEFAULT ROLE:\t\t\t {}", props.getDefaultRole());
		logger.warn("DEFAULT READ ACCESS:\t\t {}", isEmpty(props.getDefaultReadAccess()) ? "" : String.join(", ", props.getDefaultReadAccess()));
		logger.warn("DEFAULT WRITE ACCESS:\t {}", isEmpty(props.getDefaultWriteAccess()) ? "" : String.join(", ", props.getDefaultWriteAccess()));
		logger.warn("DEFAULT TAG READ ACCESS:\t {}", isEmpty(props.getDefaultTagReadAccess()) ? "" : String.join(", ", props.getDefaultTagReadAccess()));
		logger.warn("DEFAULT TAG WRITE ACCESS: {}", isEmpty(props.getDefaultTagWriteAccess()) ? "" : String.join(", ", props.getDefaultTagWriteAccess()));
		logger.warn("MAX ROLE:\t\t\t\t {}", props.getMaxRole());
		logger.warn("MIN ROLE:\t\t\t\t {}", props.getMinRole());
		logger.warn("MIN WRITE ROLE:\t\t\t {}", props.getMinWriteRole());
		logger.warn("MIN CONFIG ROLE:\t\t\t {}", props.getMinConfigRole());
		logger.warn("MIN READ BACKUPS ROLE:\t {}", props.getMinReadBackupsRole());
		logger.warn("AUTH HEADERS:\t\t\t {}", props.isAllowAuthHeaders() ? "ENABLED" : "-");
		logger.warn("USER HEADERS:\t\t\t {}", props.isAllowUserTagHeader() ? "ENABLED" : "-");
		logger.warn("ROLE HEADERS:\t\t\t {}", props.isAllowUserRoleHeader() ? "ENABLED" : "-");
		logger.warn("ROLE HEADERS:\t\t\t {}", props.isAllowLocalOriginHeader() ? "ENABLED" : "-");
		logger.warn("ORIGIN HEADERS:\t\t\t {}", props.isAllowLocalOriginHeader() ? "ENABLED" : "-");
		logger.warn("==================================================");
		logger.warn("==================================================");
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
	public AuthenticationManager noopAuthenticationManager() {
		return authentication -> {
			throw new AuthenticationServiceException("AuthenticationManager is disabled");
		};
	}

	@Bean
	JWTConfigurer securityConfigurerAdapter() {
		return new JWTConfigurer(props, tokenProvider, defaultTokenProvider, configs);
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
