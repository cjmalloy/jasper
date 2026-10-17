package jasper.component;

import feign.FeignException;
import feign.codec.DecodeException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.micrometer.core.annotation.Timed;
import jasper.client.JasperClient;
import jasper.client.dto.JasperMapper;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.Cursor;
import jasper.domain.proj.HasTags;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.InvalidPluginException;
import jasper.errors.InvalidPushException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.errors.PullLocalException;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import jasper.security.AuthFactory;
import jasper.service.dto.RefReplDto;
import jasper.service.dto.UserDto;
import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static jasper.client.JasperClient.params;
import static jasper.domain.proj.HasTags.authors;
import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static jasper.plugin.Cache.getCache;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Pull.getPull;
import static jasper.plugin.Push.getPush;
import static jasper.security.AuthoritiesConstants.MOD;
import static jasper.util.Logging.getMessage;
import static java.io.InputStream.nullInputStream;
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
	Messages messages;

	@Autowired
	AuthFactory authFactory;

	@Autowired
	Optional<FileCache> fileCache;

	boolean fileCacheMissingError = false;

	private record Log(String title, String message) {}

	/**
	 * Plugins, templates, refs, exts and users.
	 */
	private static final int STAGES = 5;
	private static final int STAGE_STEPS = 100;

	@Timed(value = "jasper.repl", histogram = true)
	public Fetch.FileRequest fetch(String url, HasTags remote) {
		var root = configs.root();
		if (!root.script("+plugin/origin/pull", remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		String[] contentType = { "" };
		InputStream[] inputStream = { null };
		tunnel.proxy(remote, baseUri -> {
			try {
				var cache = client.fetch(baseUri, url, remoteOrigin);
				if (cache.getHeaders().getContentType() != null) {
					contentType[0] = cache.getHeaders().getContentType().toString();
				}
				if (cache.getBody() == null) {
					logger.warn("{} Empty response pulling cache ({}) {}",
						remote.getOrigin(), localOrigin, url);
					if (url.startsWith("cache:")) {
						fileCache.get().push(url, localOrigin, "".getBytes());
					}
					inputStream[0] = nullInputStream();
				} else if (fileCache.isPresent()) {
					if (url.startsWith("cache:")) {
						try (var is = cache.getBody().getInputStream()) {
							fileCache.get().push(url, localOrigin, is);
						}
						inputStream[0] = fileCache.get().fetch(url, localOrigin);
					} else {
						String id;
						try (var is = cache.getBody().getInputStream()) {
							id = fileCache.get().overwrite(url, localOrigin, is, contentType[0]);
						}
						inputStream[0] = fileCache.get().fetch("cache:" + id, localOrigin);
					}
				} else {
					inputStream[0] = cache.getBody().getInputStream();
				}
			} catch (Exception e) {
				logger.warn("{} Failed to fetch from remote cache ({}) {}",
					remote.getOrigin(), remoteOrigin, url);
			}
		});
		if (inputStream[0] == null) return null;
		return new Fetch.FileRequest() {
			@Override
			public String getMimeType() {
				return contentType[0];
			}

			@Override
			public InputStream getInputStream() {
				return inputStream[0];
			}

			@Override
			public void close() throws IOException {
				inputStream[0].close();
			}
		};
	}

	@Timed(value = "jasper.repl", histogram = true)
	@Bulkhead(name = "repl")
	public void pull(Ref remote) {
		var root = configs.root();
		if (!root.script("+plugin/origin/pull", remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var pull = getPull(remote);
		var config = getOrigin(remote);
		var rootOrigin = remote.getOrigin();
		if (isBlank(config.getLocal())) throw new PullLocalException(remote.getOrigin());
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var defaultBatchSize = pull.getBatchSize() == 0 ? root.getMaxReplEntityBatch() : min(pull.getBatchSize(), root.getMaxPullEntityBatch());
		var logs = new ArrayList<Log>();
		tunnel.proxy(remote, baseUri -> {
			var progress = tagger.progress(remote.getUrl(), remote.getOrigin());
			try {
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> pluginRepository.getCursor(localOrigin), false, cursorProgress(remote.getOrigin(), progress, 0, () -> client.pluginCursor(baseUri, remoteOrigin)), (skip, size, after) -> {
					var pluginList = client.pluginPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var plugin : pluginList) {
						plugin.setOrigin(localOrigin);
						logger.trace("{} Ingesting pulled plugin {}: {}",
							remote.getOrigin(), plugin.getName(), plugin.getQualifiedTag());
						try {
							ingestPlugin.push(plugin);
						} catch (AlreadyExistsException e) {
							// Indicates a double pull
							logger.warn("{} Pulling plugin skipped (double pull detected) {}: {}",
								remote.getOrigin(), remote.getTitle(), remote.getUrl());
							return null;
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping plugin with duplicate modified date {}: {}",
								remote.getOrigin(), plugin.getName(), plugin.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of plugin with duplicate modified date %s: %s".formatted(
									plugin.getName(), plugin.getTag()), ""+plugin.getModified()));
						} catch (InvalidPushException e) {
							// Indicates a double pull
							logger.error("{} Skipping plugin with invalid data {}: {}",
								remote.getOrigin(), plugin.getName(), plugin.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of plugin with invalid data %s: %s".formatted(
									plugin.getName(), plugin.getTag()), ""+plugin.getModified()));
						}
					}
					return pluginList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> templateRepository.getCursor(localOrigin), false, cursorProgress(remote.getOrigin(), progress, 1, () -> client.templateCursor(baseUri, remoteOrigin)), (skip, size, after) -> {
					var templateList = client.templatePull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var template : templateList) {
						template.setOrigin(localOrigin);
						logger.trace("{} Ingesting pulled template {}: {}",
							remote.getOrigin(), template.getName(), template.getQualifiedTag());
						try {
							ingestTemplate.push(template);
						} catch (AlreadyExistsException e) {
							// Indicates a double pull
							logger.warn("{} Pulling template skipped (double pull detected) {}: {}",
								remote.getOrigin(), remote.getTitle(), remote.getUrl());
							return null;
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping template with duplicate modified date {}: {}",
								remote.getOrigin(), template.getName(), template.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of template with duplicate modified date %s: %s".formatted(
									template.getName(), template.getTag()), ""+template.getModified()));
						} catch (InvalidPushException e) {
							// Should not be possible
							logger.error("{} Skipping template with invalid data {}: {}",
								remote.getOrigin(), template.getName(), template.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of template with invalid data %s: %s".formatted(
									template.getName(), template.getTag()), ""+template.getModified()));
						}
					}
					return templateList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> refRepository.getCursor(localOrigin), false, cursorProgress(remote.getOrigin(), progress, 2, () -> client.refCursor(baseUri, remoteOrigin)), (skip, size, after) -> {
					logger.trace("{} Pulling batch {}", localOrigin, size);
					var refList = client.refPull(baseUri, params(
						"query", pull.getQuery(),
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var ref : refList) {
						ref.setOrigin(localOrigin);
						pull.migrate(ref, config);
						if (pull.isCachePrefetch() && ref.getUrl().startsWith("cache:") && (fileCache.isEmpty() || !fileCache.get().cacheExists(ref.getUrl(), localOrigin)) ||
							pull.isCacheProxyPrefetch() && ref.hasPlugin("_plugin/cache") && (fileCache.isEmpty() || !fileCache.get().cacheExists("cache:" + getCache(ref).getId(), localOrigin))) {
							ref.addTag("_plugin/delta/cache");
						}
						logger.trace("{} Ingesting pulled ref {}: {}",
							remote.getOrigin(), ref.getTitle(), ref.getUrl());
						try {
							ingestRef.push(rootOrigin, ref, pull.isValidatePlugins(), pull.isStripInvalidPlugins(), true);
						} catch (AlreadyExistsException e) {
							// Indicates a double pull
							logger.warn("{} Pulling batch skipped (double pull detected) {}: {}",
								remote.getOrigin(), remote.getTitle(), remote.getUrl());
							return null;
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Pulling Ref skipped with duplicate modified date {} {}: {}",
								remote.getOrigin(), ref.getModified(), remote.getTitle(), remote.getUrl());
							logs.add(new Log(
								"Pulling Ref skipped with duplicate modified date (%s): %s".formatted(
									remote.getTitle(), remote.getUrl()), ""+ref.getModified()));
						} catch (InvalidPushException e) {
							// Should not be possible
							logger.error("{} Pulling Ref skipped with invalid data {} {}: {}",
								remote.getOrigin(), ref.getModified(), remote.getTitle(), remote.getUrl());
							logs.add(new Log(
								"Pulling Ref skipped with invalid data (%s): %s".formatted(
									remote.getTitle(), remote.getUrl()), ""+ref.getModified()));
						} catch (InvalidPluginException e) {
							logger.warn("{} Failed Plugin Validation! Skipping replication of Ref ({}) {}: {}",
								remote.getOrigin(), localOrigin, remote.getTitle(), remote.getUrl());
							logs.add(new Log(
								"Failed Plugin Validation! Skipping replication of Ref (%s) %s: %s".formatted(
									localOrigin, remote.getTitle(), remote.getUrl()), getMessage(e)));
						}
					}
					if (!refList.isEmpty() && refRepository.clearIgnored(localOrigin, refList.getLast().getModified()) > 0) {
						messages.updateCursor(localOrigin, refList.getLast().getModified());
					}
					return refList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> extRepository.getCursor(localOrigin), false, cursorProgress(remote.getOrigin(), progress, 3, () -> client.extCursor(baseUri, remoteOrigin)), (skip, size, after) -> {
					var extList = client.extPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var ext : extList) {
						ext.setOrigin(localOrigin);
						logger.trace("{} Ingesting pulled ext {}: {}",
							remote.getOrigin(), ext.getName(), ext.getQualifiedTag());
						try {
							ingestExt.push(rootOrigin, ext, pull.isValidateTemplates(), pull.isStripInvalidTemplates());
						} catch (AlreadyExistsException e) {
							// Indicates a double pull
							logger.warn("{} Pulling Ext skipped (double pull detected) {}: {}",
								remote.getOrigin(), remote.getTitle(), remote.getUrl());
							return null;
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping Ext with duplicate modified date {}: {}",
								remote.getOrigin(), ext.getName(), ext.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of template with duplicate modified date %s: %s".formatted(
									ext.getName(), ext.getTag()), ""+ext.getModified()));
						} catch (InvalidTemplateException e) {
							logger.warn("{} Failed Template Validation! Skipping replication of ext {}: {}",
								remote.getOrigin(), ext.getName(), ext.getQualifiedTag());
							tagger.attachLogs(remote.getOrigin(), remote,
								"Failed Template Validation! Skipping replication of ext %s: %s".formatted(
									ext.getName(), ext.getQualifiedTag()), getMessage(e));
						}
					}
					return extList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> userRepository.getCursor(localOrigin), false, cursorProgress(remote.getOrigin(), progress, 4, () -> client.userCursor(baseUri, remoteOrigin)), (skip, size, after) -> {
					var userList = client.userPull(baseUri, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var user : userList) {
						user.setOrigin(localOrigin);
						user.setKey(null);
						pull.migrate(user, config);
						logger.trace("{} Ingesting pulled user {}: {}",
							remote.getOrigin(), user.getName(), user.getQualifiedTag());
						try {
							ingestUser.push(user);
						} catch (AlreadyExistsException e) {
							// Indicates a double pull
							logger.warn("{} Pulling User skipped (double pull detected) {}: {}",
								remote.getOrigin(), remote.getTitle(), remote.getUrl());
							return null;
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping User with duplicate modified date {}: {}",
								remote.getOrigin(), user.getName(), user.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of user with duplicate modified date %s: %s".formatted(
									user.getName(), user.getTag()), ""+user.getModified()));
						} catch (InvalidPushException e) {
							// Should not be possible
							logger.error("{} Skipping user with invalid data {}: {}",
								remote.getOrigin(), user.getName(), user.getQualifiedTag());
							logs.add(new Log(
								"Skipping replication of user with invalid data %s: %s".formatted(
									user.getName(), user.getTag()), ""+user.getModified()));
						}
					}
					return userList;
				}));
			} catch (FeignException e) {
				// Temporary connection issue, ignore
				logger.warn("{} Error pulling {} from origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), getMessage(e));
			} catch (Exception e) {
				logger.error("{} Fatal error pulling {} from origin {} {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), getMessage(e));
				tagger.attachError(remote.getOrigin(), remote,
					"Fatal error pulling %s from origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()), getMessage(e));
			} finally {
				progress.close();
				for (var log : logs) tagger.attachLogs(remote.getOrigin(), remote, log.title, log.message);
			}
		});
	}

	@Timed(value = "jasper.repl", histogram = true)
	@Bulkhead(name = "repl")
	public void push(Ref remote) {
		var root = configs.root();
		if (!root.script("+plugin/origin/push", remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var push = getPush(remote);
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var readers = readers(remote, localOrigin);
		if (readers.isEmpty()) {
			logger.error("{} No signing user can read {}, skipping push to origin ({}) {}: {}",
				remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl());
			tagger.attachError(remote.getOrigin(), remote,
				"No signing user can read %s, skipping push to origin (%s) %s: %s".formatted(
					localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()), "");
			return;
		}
		Specification<Ref> refReadSpec = readers.stream().map(Auth::refReadSpec).reduce(Specification::or).get();
		Specification<Plugin> pluginReadSpec = readers.stream().map(Auth::<Plugin>tagReadSpec).reduce(Specification::or).get();
		Specification<Template> templateReadSpec = readers.stream().map(Auth::<Template>tagReadSpec).reduce(Specification::or).get();
		Specification<Ext> extReadSpec = readers.stream().map(Auth::<Ext>tagReadSpec).reduce(Specification::or).get();
		Specification<User> userReadSpec = readers.stream().map(Auth::<User>tagReadSpec).reduce(Specification::or).get();
		var logs = new ArrayList<Log>();
		tunnel.proxy(remote, baseUri -> {
			var progress = tagger.progress(remote.getUrl(), remote.getOrigin());
			try {
				var defaultBatchSize = push.getBatchSize() == 0 ? root.getMaxReplEntityBatch() : min(push.getBatchSize(), root.getMaxPushEntityBatch());
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> client.pluginCursor(baseUri, remoteOrigin), true, cursorProgress(remote.getOrigin(), progress, 0, () -> pluginRepository.getCursor(localOrigin)), (skip, size, after) -> {
					var pluginList = pluginRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().<Plugin>spec()
								.and(pluginReadSpec),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.getContent();
					logger.debug("{} Pushing {} plugins to {}", remote.getOrigin(), pluginList.size(), remoteOrigin);
					if (!pluginList.isEmpty()) {
						client.pluginPush(baseUri, remoteOrigin, pluginList);
					}
					return pluginList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> client.templateCursor(baseUri, remoteOrigin), true, cursorProgress(remote.getOrigin(), progress, 1, () -> templateRepository.getCursor(localOrigin)), (skip, size, after) -> {
					var templateList = templateRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().<Template>spec()
								.and(templateReadSpec),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.getContent();
					logger.debug("{} Pushing {} templates to {}", remote.getOrigin(), templateList.size(), remoteOrigin);
					if (!templateList.isEmpty()) {
						client.templatePush(baseUri, remoteOrigin, templateList);
					}
					return templateList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> client.refCursor(baseUri, remoteOrigin), true, cursorProgress(remote.getOrigin(), progress, 2, () -> refRepository.getCursor(localOrigin)), (skip, size, after) -> {
					var fetched = refRepository.findAll(
							RefFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.ignored(false)
								.modifiedAfter(after)
								.build().spec()
								.and(refReadSpec),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.map(mapper::domainToDto)
						.getContent();
					var refList = fetched.stream()
						.filter(ref -> readers.stream().anyMatch(auth -> auth.canReadRef(ref)))
						.map(ref -> filter(readers, ref))
						.toList();
					logger.debug("{} Pushing {} refs to {}", remote.getOrigin(), refList.size(), remoteOrigin);
					if (!refList.isEmpty()) {
						client.refPush(baseUri, remoteOrigin, refList);
					}
					if (push.isCache()) {
						for (var ref : refList) {
							if (ref.getUrl().startsWith("cache:")) {
								if (fileCache.isPresent()) {
									try (var is = fileCache.get().fetch(ref.getUrl(), localOrigin)) {
										if (is != null) {
											client.push(baseUri, ref.getUrl(), remoteOrigin, is.readAllBytes());
										} else {
											logger.warn("{} Skip pushing empty cache ({}) {}: {}",
												remote.getOrigin(), localOrigin, ref.getTitle(), ref.getUrl());
										}
									} catch (Exception e) {
										logger.warn("{} Failed Pushing Cache! Skipping cache of ref ({}) {}: {}",
											remote.getOrigin(), localOrigin, ref.getTitle(), ref.getUrl());
										logs.add(new Log(
											"Failed Pushing Cache! Skipping cache of ref (%s) %s: %s".formatted(
												localOrigin, ref.getTitle(), ref.getUrl()),
											getMessage(e)));
									}
								} else if (!fileCacheMissingError) {
									// TODO: push to cache api
									fileCacheMissingError = true;
									logger.error("{} File cache not present! Skipping push cache of ref ({}) {}: {}",
										remote.getOrigin(), localOrigin, ref.getTitle(), ref.getUrl());
									logs.add(new Log(
										"File cache not present! Skipping push cache of ref (%s) %s: %s".formatted(
											localOrigin, ref.getTitle(), ref.getUrl()),
										"File cache not present"));
								}
							}
						}
					}
					return fetched;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> client.extCursor(baseUri, remoteOrigin), true, cursorProgress(remote.getOrigin(), progress, 3, () -> extRepository.getCursor(localOrigin)), (skip, size, after) -> {
					var extList = extRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().<Ext>spec()
								.and(extReadSpec),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.getContent();
					logger.debug("{} Pushing {} exts to {}", remote.getOrigin(), extList.size(), remoteOrigin);
					if (!extList.isEmpty()) {
						client.extPush(baseUri, remoteOrigin, extList);
					}
					return extList;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, () -> client.userCursor(baseUri, remoteOrigin), true, cursorProgress(remote.getOrigin(), progress, 4, () -> userRepository.getCursor(localOrigin)), (skip, size, after) -> {
					var userList = userRepository.findAll(
							TagFilter.builder()
								.origin(localOrigin)
								.query(push.getQuery())
								.modifiedAfter(after)
								.build().<User>spec()
								.and(userReadSpec),
							PageRequest.of(skip, size, by(Ref_.MODIFIED)))
						.map(mapper::domainToDto)
						.map(user -> filter(readers, user))
						.getContent();
					logger.debug("{} Pushing {} users to {}", remote.getOrigin(), userList.size(), remoteOrigin);
					if (!userList.isEmpty()) {
						client.userPush(baseUri, remoteOrigin, userList);
					}
					return userList;
				}));
			} catch (FeignException.Forbidden e) {
				logger.error("{} Access denied pushing {} to origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), getMessage(e));
				tagger.attachError(remote.getOrigin(), remote,
					"Access denied pushing %s to origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()), getMessage(e));
			} catch (FeignException e) {
				// Temporary connection issue, ignore
				logger.warn("{} Error pushing {} to origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), getMessage(e));
			} catch (Exception e) {
				logger.error("{} Fatal error pushing {} to origin ({}) {}: {} {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), getMessage(e));
				tagger.attachError(remote.getOrigin(), remote,
					"Fatal error pushing %s to origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()), getMessage(e));
			} finally {
				progress.close();
				for (var log : logs) tagger.attachLogs(remote.getOrigin(), remote, log.title, log.message);
			}
		});
	}

	/**
	 * The signing users of the remote origin Ref, or the default user if unsigned,
	 * that can read the local origin being pushed.
	 * Only entities that one of these users can read will be pushed.
	 */
	private List<Auth> readers(Ref remote, String localOrigin) {
		var users = authors(remote);
		return (users.isEmpty() ? List.of("") : users).stream()
			.map(user -> authFactory.create(user, remote.getOrigin()))
			.filter(auth -> auth.canReadOrigin(localOrigin))
			.toList();
	}

	/**
	 * Can any of the readers see this tag. Matches {@link jasper.service.dto.DtoMapper}.
	 */
	private static boolean canReadTag(List<Auth> readers, String tag) {
		return readers.stream().anyMatch(auth -> auth.canReadTag(tag + auth.getOrigin()));
	}

	private static List<String> filterTags(List<Auth> readers, List<String> tags) {
		if (tags == null) return null;
		return new ArrayList<>(tags.stream().filter(tag -> canReadTag(readers, tag)).toList());
	}

	/**
	 * Remove tags and plugins hidden to all readers that can read this Ref.
	 */
	private static RefReplDto filter(List<Auth> signers, RefReplDto ref) {
		var readers = signers.stream().filter(auth -> auth.canReadRef(ref)).toList();
		ref.setTags(filterTags(readers, ref.getTags()));
		if (ref.getPlugins() != null) {
			var plugins = ref.getPlugins().deepCopy();
			ref.getPlugins().fieldNames().forEachRemaining(tag -> {
				if (!canReadTag(readers, tag)) plugins.remove(tag);
			});
			ref.setPlugins(plugins.isEmpty() ? null : plugins);
		}
		return ref;
	}

	/**
	 * Remove access tags and external IDs hidden to all readers that can read this User.
	 */
	private static UserDto filter(List<Auth> signers, UserDto user) {
		var readers = signers.stream().filter(auth -> auth.canReadTag(user.getQualifiedTag())).toList();
		user.setReadAccess(filterTags(readers, user.getReadAccess()));
		user.setWriteAccess(filterTags(readers, user.getWriteAccess()));
		if (readers.stream().noneMatch(auth -> auth.hasRole(MOD) || auth.canWriteUserTag(user.getQualifiedTag()))) {
			user.setExternal(null);
		}
		return user;
	}

	/**
	 * Estimate progress within a stage from the relative position of the cursor
	 * between where it started and the latest modified date on the source.
	 */
	Consumer<Instant> cursorProgress(String origin, Tagger.Progress progress, int stage, Supplier<Instant> end) {
		if (!progress.isEnabled()) return after -> {};
		Instant last;
		try {
			last = end.get();
		} catch (Exception e) {
			logger.debug("{} Could not get cursor for progress estimate", origin, e);
			last = null;
		}
		var latest = last;
		var start = new Instant[1];
		return after -> {
			if (start[0] == null) start[0] = after;
			var value = stage * STAGE_STEPS;
			if (after != null && start[0] != null && latest != null && latest.isAfter(start[0])) {
				var total = Duration.between(start[0], latest).toMillis();
				var done = Duration.between(start[0], after).toMillis();
				value += (int) max(0, min(STAGE_STEPS - 1, done * STAGE_STEPS / max(1, total)));
			}
			progress.update(value, STAGES * STAGE_STEPS);
		};
	}

	/**
	 * Replicate in batches sorted by modified date until a batch is not full.
	 * The cursor may be null when the origin is empty, so a 413 must not end the loop.
	 * On 413 the batch size is halved, and once it reaches 1 the entity is skipped.
	 * Every batch must advance the cursor, so this always terminates.
	 * @param cursor the starting cursor, fetched with the same error rules as a batch
	 * @param canSkip pulling can't skip an entity on the remote, so a 413 with a batch size of 1 is fatal
	 * @param onBatch called with the cursor before each batch
	 */
	private List<Log> expBackoff(String origin, int batchSize, Supplier<Instant> cursor, boolean canSkip, Consumer<Instant> onBatch, ExpBackoff fn) {
		var logs = new ArrayList<Log>();
		Instant modifiedAfter;
		try {
			modifiedAfter = cursor.get();
		} catch (FeignException e) {
			throwUnlessTooLarge(e);
			throw new RuntimeException(e);
		}
		var skip = 0;
		var size = batchSize;
		while (true) {
			try {
				logger.trace("{} BATCH ({}, {}): {}",
					origin, skip, size, modifiedAfter);
				onBatch.accept(modifiedAfter);
				var list = fn.fetch(skip, size, modifiedAfter);
				if (list == null || list.isEmpty()) return logs;
				var next = list.getLast().getModified();
				if (modifiedAfter != null && !next.isAfter(modifiedAfter)) {
					throw new IllegalStateException("Replication cursor did not advance past " + modifiedAfter);
				}
				if (list.size() < size) return logs;
				modifiedAfter = next;
				skip = 0;
				if (size < batchSize) {
					size = min(batchSize, size * 2);
				}
			} catch (FeignException e) {
				throwUnlessTooLarge(e);
				if (size == 1) {
					if (!canSkip) throw new RuntimeException(modifiedAfter == null
						? "First entity is too large to replicate"
						: "Entity with modified date after " + modifiedAfter + " is too large to replicate", e);
					logger.error("{} Skipping entity with modified date after {}", origin, modifiedAfter);
					logs.add(new Log("Skipping entity with modified date after " + modifiedAfter, getMessage(e)));
					skip++;
				} else {
					size = max(1, size / 2);
					logs.add(new Log("Error replicating entities, reducing batch size to " + size, getMessage(e)));
				}
			}
		}
	}

	/**
	 * Rethrow temporary errors so the next run retries, and wrap permanent errors so they are fatal.
	 * Only returns on 413.
	 */
	private static void throwUnlessTooLarge(FeignException e) {
		if (e instanceof RetryableException) throw e;
		if (e.getCause() instanceof SSLHandshakeException) throw new RuntimeException(e);
		if (e.getCause() instanceof HttpHostConnectException) throw new RuntimeException(e);
		if (e.status() >= 500) throw e;
		if (e.status() == 403) throw new RuntimeException(e);
		// Retrying a bad response or client error will fail the same way every time
		if (e instanceof DecodeException) throw new RuntimeException(e);
		if (e.status() < 400 || e.status() == 408 || e.status() == 429) throw e;
		if (e.status() != 413) throw new RuntimeException(e);
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
		List<? extends Cursor> fetch(int skip, int size, Instant after) throws FeignException;
	}

}
