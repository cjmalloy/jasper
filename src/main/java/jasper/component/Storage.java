package jasper.component;


import jasper.errors.NotFoundException;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.origin;

@Component
public interface Storage {
	byte[] get(String origin, String namespace, String id);
	boolean exists(String origin, String namespace, String id);
	long size(String origin, String namespace, String id);
	InputStream stream(String origin, String namespace, String id);
	long stream(String origin, String namespace, String id, OutputStream os);
	Zipped streamZip(String origin, String namespace, String id) throws IOException;
	void visitTenants(PathVisitor v);
	void visitStorage(String origin, String namespace, PathVisitor v);
	List<String> listStorage(String origin, String namespace);
	void overwrite(String origin, String namespace, String id, byte[] cache) throws IOException;
	String store(String origin, String namespace, byte[] cache) throws IOException;
	void storeAt(String origin, String namespace, String id, byte[] cache) throws IOException;
	void storeAt(String origin, String namespace, String id, InputStream is) throws IOException;
	String store(String origin, String namespace, InputStream is) throws IOException;
	Zipped zipAt(String origin, String namespace, String id) throws IOException;
	void delete(String origin, String namespace, String id) throws IOException;

	default String originTenant(String origin) {
		origin = formatOrigin(origin);
		if (origin.startsWith("@")) return "at_" + origin.substring(1);
		return origin;
	}

	default String tenantOrigin(String tenant) {
		if (tenant.startsWith("at_")) tenant = "@" + tenant.substring(3);
		return origin(tenant);
	}

	default void sanitize(String ...paths) {
		for (var p : paths) {
			if (p.contains("/") || p.contains("\\")) throw new NotFoundException("Illegal characters");
		}
	}

	interface Zipped extends Closeable {
		InputStream in(String filename);
		OutputStream out(String filename) throws IOException;
	}

	interface PathVisitor {
		void visit(String filename);
	}
}
