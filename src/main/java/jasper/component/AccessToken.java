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

	private String accessToken;

	public String getAccessToken() {
		if (accessToken == null) {
			var clientId = applicationProperties.getSecurity().getAuthentication().getJwt().getClientId();
			var clientSecret = applicationProperties.getSecurity().getAuthentication().getJwt().getSecret();
			accessToken = tokenClient.tokenService(clientId, clientSecret).getAccess_token();
		}
		return accessToken;
	}
}
