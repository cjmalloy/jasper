package jasper.security.jwt;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.io.Decoders;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.net.URI;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

class JwkKeyLocator implements Locator<Key> {

	private URI jwkUri;
	private RestTemplate restTemplate;
	private Object lock = new Object();
	private volatile Map<String, Key> keyMap = new HashMap<>();

	JwkKeyLocator(URI jwkUri, RestTemplate restTemplate) {
		this.jwkUri = jwkUri;
		this.restTemplate = restTemplate;
	}

	@Override
	public Key locate(Header header) {
		return getKey(header.getKeyId());
	}

	private Key getKey(String keyId) {

		// check non synchronized to avoid a lock
		Key result = keyMap.get(keyId);
		if (result != null) {
			return result;
		}

		synchronized (lock) {
			// once synchronized, check the map once again the a previously
			// synchronized thread could have already updated they keys
			result = keyMap.get(keyId);
			if (result != null) {
				return result;
			}

			// finally, fallback to updating the keys, an return a value (or null)
			updateKeys();
			return keyMap.get(keyId);
		}
	}

	private void updateKeys() {
		Map<String, Key> newKeys = restTemplate
			.getForObject(jwkUri, JwkKeys.class)
			.getKeys().stream()
			.filter(jwkKey -> "sig".equals(jwkKey.getPublicKeyUse()))
			.filter(jwkKey -> "RSA".equals(jwkKey.getKeyType()))
			.collect(Collectors.toMap(JwkKey::getKeyId, jwkKey -> {
				BigInteger modulus = base64ToBigInteger(jwkKey.getPublicKeyModulus());
				BigInteger exponent = base64ToBigInteger(jwkKey.getPublicKeyExponent());
				RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(modulus, exponent);
				try {
					KeyFactory keyFactory = KeyFactory.getInstance("RSA");
					return keyFactory.generatePublic(rsaPublicKeySpec);
				} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
					throw new IllegalStateException("Failed to parse public key");
				}
			}));
		keyMap = Collections.unmodifiableMap(newKeys);
	}

	private BigInteger base64ToBigInteger(String value) {
		return new BigInteger(1, Decoders.BASE64URL.decode(value));
	}
}
