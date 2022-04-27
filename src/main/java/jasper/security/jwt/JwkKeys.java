package jasper.security.jwt;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwkKeys {
	private List<JwkKey> keys;
}
