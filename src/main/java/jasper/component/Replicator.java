package jasper.component;

import feign.FeignException;
import feign.RetryableException;
import io.micrometer.core.annotation.Timed;
import jasper.client.JasperClient;
import jasper.client.dto.JasperMapper;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.HasTags;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jasper.client.JasperClient.params;
import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static jasper.plugin.Cache.getCache;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Pull.getPull;
import static jasper.plugin.Push.getPush;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.domain.Sort.by;

@Component
public class Replicator {
	private static final Logger logger = LoggerFactory.getLogger(Replicator.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	JasperClient client;

	@Autowired
	Ingest ingestRef;

	@Autowired
	IngestExt ingestExt;

	@Autowired
	IngestUser ingestUser;

	@Autowired
	IngestPlugin ingestPlugin;

	@Autowired
	IngestTemplate ingestTemplate;

	@Autowired
	JasperMapper mapper;

	@Autowired
	TunnelClient tunnel;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	@Autowired
	Optional<FileCache> fileCache;

	boolean fileCacheMissingError = false;

	record Log(String title, String message) {}

	@Timed(value = "jasper.repl", histogram = true)
	public Fetch.FileRequest fetch(String url, HasTags remote) {
		var root = configs.root();
		if (!root.script("+plugin/origin/pull", remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		String[] contentType = { "" };
		byte[][] data = { null };
		tunnel.proxy(remote, baseUri -> {
			try {
				var cache = client.fetch(baseUri, url, remoteOrigin);
				if (fileCache.isPresent()) {
					if (cache.getBody() != null) {
						data[0] = cache.getBody();
						fileCache.get().push(url, localOrigin, cache.getBody());
					} else {
						fileCache.get().push(url, localOrigin, "".getBytes());
						logger.warn("{} Empty response pulling cache ({}) {}",
							remote.getOrigin(), localOrigin, url);
					}
				}
				if (cache.getHeaders().getContentType() != null) {
					contentType[0] = cache.getHeaders().getContentType().toString();
				}
			} catch (IOException e) {
				logger.warn("{} Failed to fetch from remote cache ({}) {}",
					remote.getOrigin(), remoteOrigin, url);
			}
		});
		if (data[0] == null) return null;
		var inputStream = new ByteArrayInputStream(data[0]);
		return new Fetch.FileRequest() {
			@Override
			public String getMimeType() {
				return contentType[0];
			}

			@Override
			public InputStream getInputStream() {
				return inputStream;
			}

			@Override
			public void close() throws IOException {
				inputStream.close();
			}
		};
	}

	@Timed(value = "jasper.repl", histogram = true)
	public void pull(Ref remote) {
		var root = configs.root();
		if (!root.script("+plugin/origin/pull", remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var pull = getPull(remote);
		var config = getOrigin(remote);
		var rootOrigin = remote.getOrigin();
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var defaultBatchSize = pull.getBatchSize() == 0 ? root.getMaxReplEntityBatch() : min(pull.getBatchSize(), root.getMaxPullEntityBatch());
		var logs = new ArrayList<Log>();
		tunnel.proxy(remote, baseUri -> {
			try {
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, pluginRepository.getCursor(localOrigin), (skip, size, after) -> {
					var pluginList = client.pluginPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var plugin : pluginList) {
						plugin.setOrigin(localOrigin);
						try {
							ingestPlugin.push(plugin);
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping plugin with duplicate modified date {}: {}",
								remote.getOrigin(), plugin.getName(), plugin.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of plugin with duplicate modified date %s: %s".formatted(
									plugin.getName(), plugin.getTag()),
								""+plugin.getModified()));
						}
					}
					return pluginList.size() == size ? pluginList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, templateRepository.getCursor(localOrigin), (skip, size, after) -> {
					var templateList = client.templatePull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var template : templateList) {
						template.setOrigin(localOrigin);
						try {
							ingestTemplate.push(template);
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping template with duplicate modified date {}: {}",
								remote.getOrigin(), template.getName(), template.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of template with duplicate modified date %s: %s".formatted(
									template.getName(), template.getTag()),
								""+template.getModified()));
						}
					}
					return templateList.size() == size ? templateList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, refRepository.getCursor(localOrigin), (skip, size, after) -> {
					var refList = client.refPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var ref : refList) {
						ref.setOrigin(localOrigin);
						pull.migrate(ref, config);
						try {
							ingestRef.push(ref, rootOrigin, pull.isValidatePlugins(), pull.isGenerateMetadata());
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping Ref with duplicate modified date {}: {}",
								remote.getOrigin(), ref.getTitle(), ref.getUrl());
							logs.add(new Log(
								"Skipping replication of Ref with duplicate modified date %s: %s".formatted(
									ref.getTitle(), ref.getUrl()),
								""+ref.getModified()));
						} catch (RuntimeException e) {
							logger.warn("{} Failed Plugin Validation! Skipping replication of ref ({}) {}: {}",
								remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl());
							logs.add(new Log(
								"Failed Plugin Validation! Skipping replication of ref %s %s: %s".formatted(
									ref.getOrigin(), ref.getTitle(), ref.getUrl()),
								e.getMessage()));
						}
						if (pull.isCache() && ref.getUrl().startsWith("cache:") && !fileCache.get().cacheExists(ref.getUrl(), ref.getOrigin()) ||
							pull.isCacheProxyPrefetch() && ref.hasPlugin("_plugin/cache") && !fileCache.get().cacheExists("cache:" + getCache(ref).getId(), ref.getOrigin())) {
							if (fileCache.isPresent()) {
								try {
									var cache = client.fetch(baseUri, ref.getUrl(), remoteOrigin);
									if (cache.getBody() != null) {
										fileCache.get().push(ref.getUrl(), ref.getOrigin(), cache.getBody());
									} else {
										fileCache.get().push(ref.getUrl(), ref.getOrigin(), "".getBytes());
										logger.warn("{} Empty response pulling cache ({}) {}: {}",
											remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl());
									}
								} catch (Exception e) {
									logger.warn("{} Failed Pulling Cache! Skipping cache of ref ({}) {}: {}",
										remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl(), e);
									logs.add(new Log(
										"Failed Pulling Cache! Skipping cache of ref %s %s: %s".formatted(
											ref.getOrigin(), ref.getTitle(), ref.getUrl()),
										e.getMessage()));
								}
							} else if (!fileCacheMissingError) {
								// TODO: pull from to cache api
								fileCacheMissingError = true;
								logger.error("{} File cache not present! Skipping pull cache of ref ({}) {}: {}",
									remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl());
								logs.add(new Log(
									"File cache not present! Skipping pull cache of ref %s %s: %s".formatted(
										ref.getOrigin(), ref.getTitle(), ref.getUrl()),
									"File cache not present"));
							}
						}
					}
					return refList.size() == size ? refList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, extRepository.getCursor(localOrigin), (skip, size, after) -> {
					var extList = client.extPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var ext : extList) {
						ext.setOrigin(localOrigin);
						try {
							ingestExt.push(ext, rootOrigin, pull.isValidateTemplates());
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping Ext with duplicate modified date {}: {}",
								remote.getOrigin(), ext.getName(), ext.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of template with duplicate modified date %s: %s".formatted(
									ext.getName(), ext.getTag()),
								""+ext.getModified()));
						} catch (RuntimeException e) {
							logger.warn("{} Failed Template Validation! Skipping replication of ext {}: {}",
								remote.getOrigin(), ext.getName(), ext.getQualifiedTag());
							tagger.attachLogs(remote.getOrigin(), remote,
								"Failed Template Validation! Skipping replication of ext %s: %s".formatted(
									ext.getName(), ext.getQualifiedTag()),
								e.getMessage());
						}
					}
					return extList.size() == size ? extList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, userRepository.getCursor(localOrigin), (skip, size, after) -> {
					var userList = client.userPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var user : userList) {
						user.setOrigin(localOrigin);
						user.setKey(null);
						pull.migrate(user, config);
						try {
							ingestUser.push(user);
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping User with duplicate modified date {}: {}",
								remote.getOrigin(), user.getName(), user.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of user with duplicate modified date %s: %s".formatted(
									user.getName(), user.getTag()),
								""+user.getModified()));
						}
					}
					return userList.size() == size ? userList.getLast().getModified() : null;
				}));
			} catch (FeignException e) {
				// Temporary connection issue, ignore
				logger.warn("{} Error pulling {} from origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e.getMessage());
			} catch (Exception e) {
				logger.error("{} Error pulling {} from origin {} {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e.getMessage());
				tagger.attachError(remote.getOrigin(), remote,
					"Error pulling %s from origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()),
					e.getMessage());
			} finally {
				for (var log : logs) tagger.attachLogs(remote.getOrigin(), remote, log.title, log.message);
			}
		});
	}

	@Timed(value = "jasper.repl", histogram = true)
	public void push(Ref remote) {
		var root = configs.root();
		if (!root.script("+plugin/origin/push", remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var push = getPush(remote);
		// TODO: only push what user can see
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var logs = new ArrayList<Log>();
		tunnel.proxy(remote, baseUri -> {
			try {
				var defaultBatchSize = push.getBatchSize() == 0 ? root.getMaxReplEntityBatch() : min(push.getBatchSize(), root.getMaxPushEntityBatch());
				var modifiedAfter = client.pluginCursor(baseUri, remoteOrigin);
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, modifiedAfter, (skip, size, after) -> {
					var pluginList = pluginRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().spec(),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.getContent();
					logger.debug("{} Pushing {} plugins to {}", remote.getOrigin(), pluginList.size(), remoteOrigin);
					if (!pluginList.isEmpty()) {
						client.pluginPush(baseUri, remoteOrigin, pluginList);
					}
					return pluginList.size() == size ? pluginList.getLast().getModified() : null;
				}));

				modifiedAfter = client.templateCursor(baseUri, remoteOrigin);
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, modifiedAfter, (skip, size, after) -> {
					var templateList = templateRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().spec(),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.getContent();
					logger.debug("{} Pushing {} templates to {}", remote.getOrigin(), templateList.size(), remoteOrigin);
					if (!templateList.isEmpty()) {
						client.templatePush(baseUri, remoteOrigin, templateList);
					}
					return templateList.size() == size ? templateList.getLast().getModified() : null;
				}));

				modifiedAfter = client.refCursor(baseUri, remoteOrigin);
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, modifiedAfter, (skip, size, after) -> {
					var refList = refRepository.findAll(
							RefFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().spec(),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.map(mapper::domainToDto)
						.getContent();
					logger.debug("{} Pushing {} refs to {}", remote.getOrigin(), refList.size(), remoteOrigin);
					if (!refList.isEmpty()) {
						client.refPush(baseUri, remoteOrigin, refList);
					}
					if (push.isCache()) {
						for (var ref : refList) {
							if (ref.getUrl().startsWith("cache:")) {
								if (fileCache.isPresent()) {
									try {
										var data = fileCache.get().fetchBytes(ref.getUrl(), ref.getOrigin());
										if (data != null) {
											client.push(baseUri, ref.getUrl(), remoteOrigin, data);
										} else {
											logger.warn("{} Skip pushing empty cache ({}) {}: {}",
												remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl());
										}
									} catch (Exception e) {
										logger.warn("{} Failed Pushing Cache! Skipping cache of ref ({}) {}: {}",
											remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl(), e);
										logs.add(new Log(
											"Failed Pushing Cache! Skipping cache of ref %s %s: %s".formatted(
												ref.getOrigin(), ref.getTitle(), ref.getUrl()),
											e.getMessage()));
									}
								} else if (!fileCacheMissingError) {
									// TODO: push to cache api
									fileCacheMissingError = true;
									logger.error("{} File cache not present! Skipping push cache of ref ({}) {}: {}",
										remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl());
									logs.add(new Log(
										"File cache not present! Skipping push cache of ref %s %s: %s".formatted(
											ref.getOrigin(), ref.getTitle(), ref.getUrl()),
										"File cache not present"));
								}
							}
						}
					}
					return refList.size() == size ? refList.getLast().getModified() : null;
				}));

				modifiedAfter = client.extCursor(baseUri, remoteOrigin);
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, modifiedAfter, (skip, size, after) -> {
					var extList = extRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().spec(),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.getContent();
					logger.debug("{} Pushing {} exts to {}", remote.getOrigin(), extList.size(), remoteOrigin);
					if (!extList.isEmpty()) {
						client.extPush(baseUri, remoteOrigin, extList);
					}
					return extList.size() == size ? extList.getLast().getModified() : null;
				}));

				modifiedAfter = client.userCursor(baseUri, remoteOrigin);
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, modifiedAfter, (skip, size, after) -> {
					var userList = userRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().spec(),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.map(mapper::domainToDto)
						.getContent();
					logger.debug("{} Pushing {} users to {}", remote.getOrigin(), userList.size(), remoteOrigin);
					if (!userList.isEmpty()) {
						client.userPush(baseUri, remoteOrigin, userList);
					}
					return userList.size() == size ? userList.getLast().getModified() : null;
				}));
			} catch (FeignException e) {
				// Temporary connection issue, ignore
				logger.warn("{} Error pushing {} to origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e.getMessage());
			} catch (Exception e) {
				logger.error("{} Error pushing {} to origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e.getMessage());
				tagger.attachError(remote.getOrigin(), remote,
					"Error pushing %s to origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()),
					e.getMessage());
			} finally {
				for (var log : logs) tagger.attachLogs(remote.getOrigin(), remote, log.title, log.message);
			}
		});
	}

	private List<Log> expBackoff(String origin, int batchSize, Instant modifiedAfter, ExpBackoff fn) {
		var logs = new ArrayList<Log>();
		var skip = 0;
		var size = batchSize;
		do {
			try {
				modifiedAfter = fn.fetch(skip, size, modifiedAfter);
				skip = 0;
				if (size < batchSize) {
					size = min(batchSize, size * 2);
				}
			} catch (FeignException e) {
				if (e instanceof RetryableException) throw e;
				if (e.getCause() instanceof SSLHandshakeException) throw new RuntimeException(e);
				if (e.getCause() instanceof HttpHostConnectException) throw new RuntimeException(e);
				if (e.status() >= 500) throw e;
				if (e.status() == 403) throw new RuntimeException(e);
				if (e.status() != 413) throw e;
				if (size == 1) {
					logger.error("{} Skipping entity with modified date after {}", origin, modifiedAfter);
					logs.add(new Log("Skipping entity with modified date after " + modifiedAfter, e.getMessage()));
					skip++;
				} else {
					logs.add(new Log("Error pulling entities, reducing batch size to " + size, e.getMessage()));
					size = max(1, size / 2);
				}
			}
		} while (modifiedAfter != null);
		return logs;
	}

	public static boolean isDeletorTag(String tag) {
		tag = localTag(tag);
		return tag.equals("deleted") || tag.endsWith("/deleted");
	}

	public static String deletorTag(String tag) {
		if (isBlank(tag)) return "deleted";
		return localTag(tag) + "/deleted" + tagOrigin(tag);
	}

	public static String deletedTag(String deletor) {
		var local = localTag(deletor);
		if (local.equals("deleted")) return "";
		return local.substring(0, local.length() - "/deleted".length()) + tagOrigin(deletor);
	}

	interface ExpBackoff {
		Instant fetch(int skip, int size, Instant after) throws FeignException;
	}

}
