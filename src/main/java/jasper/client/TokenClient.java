package jasper.client;

import feign.Body;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import jasper.component.dto.AuthTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "tokenclient", url = "${application.security.authentication.jwt.token-endpoint}")
public interface TokenClient {
	@RequestLine("POST /")
	@Headers("Content-Type: application/x-www-form-urlencoded")
	@Body("client_id={clientId}&client_secret={clientSecret}&scope=admin&grant_type=client_credentials")
	AuthTokenResponse tokenService(@Param("clientId") String clientId, @Param("clientSecret") String clientSecret);
}
