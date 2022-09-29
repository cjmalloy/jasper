package jasper.component;

import jasper.client.TokenClient;
import jasper.config.Props;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AccessToken {
	@Autowired
	Props props;

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
		return props.getSecurity().getAuthentication().getJwt().getSecret();
	}

	private String getClientId() {
		return props.getSecurity().getAuthentication().getJwt().getClientId();
	}
}
