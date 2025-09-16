package jasper.security.jwt;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JwkKeys {
	private List<JwkKey> keys;
}
