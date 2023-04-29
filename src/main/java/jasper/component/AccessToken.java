package jasper.component;

import jasper.client.TokenClient;
import jasper.config.Props;
import jasper.security.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.net.URI;
import java.net.URISyntaxException;

@Component
@RequestScope
public class AccessToken {

	@Autowired
	Props props;

	@Autowired
	Auth auth;

	@Autowired
	TokenClient tokenClient;

	private String adminToken;

	public String getAdminToken() {
		if (adminToken == null) {
			adminToken = tokenClient.tokenService(baseUri(), getClientId(), getSecret(), "admin").getAccess_token();
		}
		return adminToken;
	}

	private URI baseUri() {
		try {
			return new URI(auth.getClient().getAuthentication().getJwt().getTokenEndpoint());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private String getSecret() {
		return props.getSecurity().getClient(props.getLocalOrigin()).getAuthentication().getJwt().getSecret();
	}

	private String getClientId() {
		return props.getSecurity().getClient(props.getLocalOrigin()).getAuthentication().getJwt().getClientId();
	}
}
