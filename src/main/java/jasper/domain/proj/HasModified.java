package jasper.domain.proj;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public interface HasModified {
	Instant getModified();

	ObjectMapper om = new ObjectMapper();
}
