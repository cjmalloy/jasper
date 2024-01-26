package jasper.component;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;

@Profile("!storage")
@Component
public class StorageImplNop implements Storage {
	@Override
	public byte[] get(String origin, String id) {
		return null;
	}

	@Override
	public long size(String origin, String id) {
		return 0;
	}

	@Override
	public long stream(String origin, String id, OutputStream os) {
		return 0;
	}

	@Override
	public void overwrite(String origin, String id, byte[] cache) { }

	@Override
	public String store(String origin, byte[] cache) {
		return null;
	}

	@Override
	public String store(String origin, InputStream is) {
		return null;
	}

	@Override
	public void delete(String origin, String id) { }
}
