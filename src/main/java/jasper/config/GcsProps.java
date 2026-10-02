package jasper.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Bucket routing for Google Cloud Storage, bound to {@code application.storage.gcs}.
 */
@Getter
@Setter
public class GcsProps {
	/**
	 * Bucket for any tenant and namespace not matched by a route. Never served by a CDN.
	 */
	private String bucket;
	/**
	 * Folder used to stage zip archives.
	 */
	private String tmpDir = System.getProperty("java.io.tmpdir");
	/**
	 * Routes checked in order. The first route matching both the tenant and namespace is used.
	 */
	private List<Route> routes = new ArrayList<>();

	@Getter
	@Setter
	public static class Route {
		/**
		 * Bucket to store matching objects in.
		 */
		private String bucket;
		/**
		 * Namespaces matched by this route. Matches all namespaces when empty.
		 */
		private List<String> namespaces = new ArrayList<>();
		/**
		 * Tenants matched by this route. Use "default" for the default tenant.
		 * Matches all tenants when empty.
		 */
		private List<String> tenants = new ArrayList<>();
		/**
		 * Public CDN host serving this bucket. Leave blank if the bucket is not public.
		 */
		private String cdnBaseUrl = "";
	}
}
