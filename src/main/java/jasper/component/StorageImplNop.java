package jasper.component;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@Profile("!storage")
@Component
public class StorageImplNop implements Storage {
	@Override
	public byte[] get(String origin, String namespace, String id) {
		return null;
	}

	@Override
	public boolean exists(String origin, String namespace, String id) {
		return false;
	}

	@Override
	public long size(String origin, String namespace, String id) {
		return 0;
	}

	@Override
	public long stream(String origin, String namespace, String id, OutputStream os) {
		return 0;
	}

	@Override
	public Zipped streamZip(String origin, String namespace, String id) throws IOException {
		return null;
	}

	@Override
	public void visitTenants(StorageImplLocal.PathVisitor v) { }

	@Override
	public void visitStorage(String origin, String namespace, StorageImplLocal.PathVisitor v) { }

	@Override
	public List<String> listStorage(String origin, String namespace) {
		return null;
	}

	@Override
	public void overwrite(String origin, String namespace, String id, byte[] cache) { }

	@Override
	public String store(String origin, String namespace, byte[] cache) {
		return null;
	}

	@Override
	public void storeAt(String origin, String namespace, String id, byte[] cache) { }

	@Override
	public void storeAt(String origin, String namespace, String id, InputStream is) throws IOException { }

	@Override
	public String store(String origin, String namespace, InputStream is) {
		return null;
	}

	@Override
	public Zipped zipAt(String origin, String namespace, String id) throws IOException {
		return null;
	}

	@Override
	public void delete(String origin, String namespace, String id) { }
}
