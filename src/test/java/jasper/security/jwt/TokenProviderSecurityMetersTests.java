package jasper.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jasper.component.ConfigCache;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import jasper.config.Props;
import jasper.management.SecurityMetersService;
import jasper.security.AuthoritiesConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenProviderSecurityMetersTests {

    private static final int ONE_MINUTE = 60;
    private static final String INVALID_TOKENS_METER_EXPECTED_NAME = "security.authentication.invalid-tokens";

    private MeterRegistry meterRegistry;

    private TokenProviderImpl tokenProvider;
	private ConfigCache configCache = getConfigs();

	ConfigCache getConfigs() {
		var root = new ServerConfig();
		var security = new SecurityConfig();
		var configCache = mock(ConfigCache.class);
		when(configCache.root()).thenReturn(root);
		when(configCache.security(anyString())).thenReturn(security);
		return configCache;
	}

    @BeforeEach
    public void setup() {
		var security = configCache.security("");
		security.setMode("jwt");
		security.setClientId("");
		security.setBase64Secret("d54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8");

        meterRegistry = new SimpleMeterRegistry();

        SecurityMetersService securityMetersService = new SecurityMetersService(meterRegistry);

        tokenProvider = new TokenProviderImpl(new Props(), configCache, securityMetersService, null);
    }

    @Test
    void testValidTokenShouldNotCountAnything() {
        Collection<Counter> counters = meterRegistry.find(INVALID_TOKENS_METER_EXPECTED_NAME).counters();

        assertThat(aggregate(counters)).isZero();

        String validToken = createValidToken();

        tokenProvider.validateToken(validToken, "");

        assertThat(aggregate(counters)).isZero();
    }

    @Test
    void testTokenExpiredCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "expired").counter().count()).isZero();

        String expiredToken = createExpiredToken();

        tokenProvider.validateToken(expiredToken, "");

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "expired").counter().count()).isEqualTo(1);
    }

    @Test
    void testTokenUnsupportedCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "unsupported").counter().count()).isZero();

        String unsupportedToken = createUnsupportedToken();

        tokenProvider.validateToken(unsupportedToken, "");

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "unsupported").counter().count()).isEqualTo(1);
    }

    @Test
    void testTokenSignatureInvalidCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "invalid-signature").counter().count()).isZero();

        String tokenWithDifferentSignature = createTokenWithDifferentSignature();

        tokenProvider.validateToken(tokenWithDifferentSignature, "");

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "invalid-signature").counter().count()).isEqualTo(1);
    }

    @Test
    void testTokenMalformedCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "malformed").counter().count()).isZero();

        String malformedToken = createMalformedToken();

        tokenProvider.validateToken(malformedToken, "");

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "malformed").counter().count()).isEqualTo(1);
    }

    private String createValidToken() {
        Authentication authentication = createAuthentication();
        return tokenProvider.createToken(authentication, 1800);
    }

    private String createExpiredToken() {
        Authentication authentication = createAuthentication();
        return tokenProvider.createToken(authentication, -ONE_MINUTE);
    }

    private Authentication createAuthentication() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(AuthoritiesConstants.ANONYMOUS));
        return new UsernamePasswordAuthenticationToken("anonymous", "anonymous", authorities);
    }

    private String createUnsupportedToken() {
		var security = configCache.security("");

        return Jwts.builder().setPayload("payload").signWith(Keys.hmacShaKeyFor(security.getSecretBytes()), SignatureAlgorithm.HS256).compact();
    }

    private String createMalformedToken() {
        String validToken = createValidToken();

        return "X" + validToken;
    }

    private String createTokenWithDifferentSignature() {
        Key otherKey = Keys.hmacShaKeyFor(
            Decoders.BASE64.decode("abfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8")
        );

        return Jwts
            .builder()
            .subject("anonymous")
            .signWith(otherKey, SignatureAlgorithm.HS512)
            .expiration(new Date(new Date().getTime() + ONE_MINUTE))
            .compact();
    }

    private double aggregate(Collection<Counter> counters) {
        return counters.stream().mapToDouble(Counter::count).sum();
    }
}
