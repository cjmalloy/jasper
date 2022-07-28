package jasper.component;

import jasper.client.TokenClient;
import jasper.config.ApplicationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AccessToken {
	@Autowired
	ApplicationProperties applicationProperties;

	@Autowired
	TokenClient tokenClient;

	private String adminToken;

	public String getAdminToken() {
		if (adminToken == null) {
			adminToken = tokenClient.tokenService(getClientId(), getSecret(), "admin").getAccess_token();
		}
		return adminToken;
	}

	private String getSecret() {
		return applicationProperties.getSecurity().getAuthentication().getJwt().getSecret();
	}

	private String getClientId() {
		return applicationProperties.getSecurity().getAuthentication().getJwt().getClientId();
	}
}
