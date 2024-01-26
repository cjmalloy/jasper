package jasper.component;


import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Component
public interface Storage {
	byte[] get(String origin, String id);
	long size(String origin, String id);
	long stream(String origin, String id, OutputStream os);
	void overwrite(String origin, String id, byte[] cache) throws IOException;
	String store(String origin, byte[] cache) throws IOException;
	String store(String origin, InputStream is) throws IOException;
	void delete(String origin, String id) throws IOException;
}
