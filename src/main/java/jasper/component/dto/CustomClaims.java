package jasper.component.dto;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CustomClaims {
	private String auth;

	public CustomClaims setRoles(String[] value) {
		auth = String.join(",", value);
		return this;
	}
}
