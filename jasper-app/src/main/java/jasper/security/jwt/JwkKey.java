package jasper.security.jwt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwkKey {
	@JsonProperty("kty")
	private String keyType;
	@JsonProperty("alg")
	private String algorithm;
	@JsonProperty("kid")
	private String keyId;
	@JsonProperty("use")
	private String publicKeyUse;
	@JsonProperty("e")
	private String publicKeyExponent;
	@JsonProperty("n")
	private String publicKeyModulus;
}
