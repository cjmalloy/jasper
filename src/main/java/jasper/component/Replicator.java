package jasper.component;

import feign.RetryableException;
import io.micrometer.core.annotation.Timed;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jasper.client.JasperClient;
import jasper.client.dto.JasperMapper;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.plugin.Push;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jasper.client.JasperClient.params;
import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Pull.getPull;
import static jasper.plugin.Push.getPush;
import static java.lang.Math.max;
import static java.lang.Math.min;
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

	@Timed(value = "jasper.repl", histogram = true)
	public void pull(Ref remote) {
		var root = configs.root();
		if (!root.getPullOrigins().contains(remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var pull = getPull(remote);
		var config = getOrigin(remote);
		var rootOrigin = remote.getOrigin();
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		var defaultBatchSize = pull.getBatchSize() == 0 ? root.getMaxPullEntityBatch() : min(pull.getBatchSize(), root.getMaxPullEntityBatch());
		var logs = new ArrayList<Tuple2<String, String>>();
		tunnel.proxy(remote, url -> {
			try {
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, pluginRepository.getCursor(localOrigin), (skip, size, after) -> {
					var pluginList = client.pluginPull(url, params(
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
							logs.add(Tuple.of(
								"Skipping replication of plugin with duplicate modified date %s: %s".formatted(
									plugin.getName(), plugin.getTag()),
								""+plugin.getModified()));
						}
					}
					return pluginList.size() == size ? pluginList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, templateRepository.getCursor(localOrigin), (skip, size, after) -> {
					var templateList = client.templatePull(url, params(
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
							logs.add(Tuple.of(
								"Skipping replication of template with duplicate modified date %s: %s".formatted(
									template.getName(), template.getTag()),
								""+template.getModified()));
						}
					}
					return templateList.size() == size ? templateList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, refRepository.getCursor(localOrigin), (skip, size, after) -> {
					var refList = client.refPull(url, params(
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
							logs.add(Tuple.of(
								"Skipping replication of Ref with duplicate modified date %s: %s".formatted(
									ref.getTitle(), ref.getUrl()),
								""+ref.getModified()));
						} catch (RuntimeException e) {
							logger.warn("{} Failed Plugin Validation! Skipping replication of ref ({}) {}: {}",
								remote.getOrigin(), ref.getOrigin(), ref.getTitle(), ref.getUrl());
							logs.add(Tuple.of(
								"Failed Plugin Validation! Skipping replication of ref %s %s: %s".formatted(
									ref.getOrigin(), ref.getTitle(), ref.getUrl()),
								e.getMessage()));
						}
					}
					return refList.size() == size ? refList.getLast().getModified() : null;
				}));
				logs.addAll(expBackoff(remote.getOrigin(), defaultBatchSize, extRepository.getCursor(localOrigin), (skip, size, after) -> {
					var extList = client.extPull(url, params(
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
							logs.add(Tuple.of(
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
					var userList = client.userPull(url, params(
						"size", size,
						"origin", remoteOrigin,
						"modifiedAfter", after));
					for (var user : userList) {
						user.setOrigin(localOrigin);
						pull.migrate(user, config);
						var maybeExisting = userRepository.findOneByQualifiedTag(user.getQualifiedTag());
						if (maybeExisting.isPresent() && user.getKey() == null) {
							user.setKey(maybeExisting.get().getKey());
						}
						try {
							ingestUser.push(user);
						} catch (DuplicateModifiedDateException e) {
							// Should not be possible
							logger.error("{} Skipping User with duplicate modified date {}: {}",
								remote.getOrigin(), user.getName(), user.getQualifiedTag());
							logs.add(Tuple.of(
								"Skipping replication of user with duplicate modified date %s: %s".formatted(
									user.getName(), user.getTag()),
								""+user.getModified()));
						}
					}
					return userList.size() == size ? userList.getLast().getModified() : null;
				}));
			} catch (RetryableException e) {
				// Temporary connection issue, ignore
				logger.warn("{} Error pushing {} to origin ({}) {}: {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e);
			} catch (Exception e) {
				logger.error("{} Error pulling {} from origin {} {}: {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e);
				tagger.attachError(remote.getOrigin(), remote,
					"Error pulling %s from origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()),
					e.getMessage());
			} finally {
				for (var log : logs) tagger.attachLogs(remote.getOrigin(), remote, log._1, log._2);
			}
		});
	}

	@Timed(value = "jasper.repl", histogram = true)
	public void push(Ref remote) {
		var root = configs.root();
		if (!root.getPushOrigins().contains(remote.getOrigin())) throw new OperationForbiddenOnOriginException(remote.getOrigin());
		var push = getPush(remote);
		var config = getOrigin(remote);
		var localOrigin = origin(config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		if (!push.isCheckRemoteCursor() && allPushed(push, localOrigin))  {
			logger.debug("{} Skipping push, up to date {}", remote.getOrigin(), remoteOrigin);
			return;
		}
		var logs = new ArrayList<Tuple2<String, String>>();
		tunnel.proxy(remote, url -> {
			try {
				var defaultBatchSize = push.getBatchSize() == 0 ? root.getMaxPushEntityBatch() : min(push.getBatchSize(), root.getMaxPushEntityBatch());
				var modifiedAfter = push.isCheckRemoteCursor() ? client.pluginCursor(url, remoteOrigin) : push.getLastModifiedPluginWritten();
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
						client.pluginPush(url, remoteOrigin, pluginList);
						push.setLastModifiedPluginWritten(pluginList.getLast().getModified());
					}
					return pluginList.size() == size ? pluginList.getLast().getModified() : null;
				}));

				modifiedAfter = push.isCheckRemoteCursor() ? client.templateCursor(url, remoteOrigin) : push.getLastModifiedTemplateWritten();
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
						client.templatePush(url, remoteOrigin, templateList);
						push.setLastModifiedTemplateWritten(templateList.getLast().getModified());
					}
					return templateList.size() == size ? templateList.getLast().getModified() : null;
				}));

				modifiedAfter = push.isCheckRemoteCursor() ? client.refCursor(url, remoteOrigin) : push.getLastModifiedRefWritten();
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
						client.refPush(url, remoteOrigin, refList);
						push.setLastModifiedRefWritten(refList.getLast().getModified());
					}
					return refList.size() == size ? refList.getLast().getModified() : null;
				}));

				modifiedAfter = push.isCheckRemoteCursor() ? client.extCursor(url, remoteOrigin) : push.getLastModifiedExtWritten();
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
						client.extPush(url, remoteOrigin, extList);
						push.setLastModifiedExtWritten(extList.getLast().getModified());
					}
					return extList.size() == size ? extList.getLast().getModified() : null;
				}));

				modifiedAfter = push.isCheckRemoteCursor() ? client.userCursor(url, remoteOrigin) : push.getLastModifiedUserWritten();
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
						client.userPush(url, remoteOrigin, userList);
						push.setLastModifiedUserWritten(userList.getLast().getModified());
					}
					return userList.size() == size ? userList.getLast().getModified() : null;
				}));
			} catch (RetryableException e) {
				// Temporary connection issue, ignore
				logger.warn("{} Error pushing {} to origin ({}) {}: {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e);
			} catch (Exception e) {
				logger.error("{} Error pushing {} to origin ({}) {}: {}",
					remote.getOrigin(), localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl(), e);
				tagger.attachError(remote.getOrigin(), remote,
					"Error pushing %s to origin (%s) %s: %s".formatted(
						localOrigin, remoteOrigin, remote.getTitle(), remote.getUrl()),
					e.getMessage());
			} finally {
				for (var log : logs) tagger.attachLogs(remote.getOrigin(), remote, log._1, log._2);
			}
		});
		remote.setPlugin("+plugin/origin/push", push);
		refRepository.save(remote);
	}

	private boolean allPushed(Push push, String localOrigin) {
		if (refRepository.count(
				RefFilter.builder()
					.origin(localOrigin)
					.query(push.getQuery())
					.modifiedAfter(push.getLastModifiedRefWritten())
					.build().spec()) > 0) {
			return false;
		}
		if (extRepository.count(
				TagFilter.builder()
					.origin(localOrigin)
					.query(push.getQuery())
					.modifiedAfter(push.getLastModifiedExtWritten())
					.build().spec()) > 0) {
			return false;
		}
		if (userRepository.count(
				TagFilter.builder()
					.origin(localOrigin)
					.query(push.getQuery())
					.modifiedAfter(push.getLastModifiedUserWritten())
					.build().spec()) > 0) {
			return false;
		}
		if (pluginRepository.count(
			TagFilter.builder()
				.origin(localOrigin)
				.query(push.getQuery())
				.modifiedAfter(push.getLastModifiedPluginWritten())
				.build().spec()) > 0) {
			return false;
		}
		if (templateRepository.count(
			TagFilter.builder()
				.origin(localOrigin)
				.query(push.getQuery())
				.modifiedAfter(push.getLastModifiedTemplateWritten())
				.build().spec()) > 0) {
			return false;
		}
		return true;
	}

	private List<Tuple2<String, String>> expBackoff(String origin, int batchSize, Instant modifiedAfter, ExpBackoff fn) {
		var logs = new ArrayList<Tuple2<String, String>>();
		var skip = 0;
		var size = batchSize;
		do {
			try {
				modifiedAfter = fn.fetch(skip, size, modifiedAfter);
				skip = 0;
				if (size < batchSize) {
					size = min(batchSize, size * 2);
				}
			} catch (RetryableException e) {
				if (e.getCause() instanceof SSLHandshakeException) throw e;
				if (e.getCause() instanceof HttpHostConnectException) throw e;
				if (e.getCause() instanceof NoHttpResponseException) throw e;
				if (size == 1) {
					logger.error("{} Skipping entity with modified date after {}", origin, modifiedAfter);
					logs.add(Tuple.of("Skipping plugin with modified date after " + modifiedAfter, e.getMessage()));
					skip++;
				} else {
					logs.add(Tuple.of("Error pulling plugins, reducing batch size to " + size, e.getMessage()));
					size = max(1, size / 2);
				}
			}
		} while (modifiedAfter != null);
		return logs;
	}

	public static boolean isDeletorTag(String tag) {
		return localTag(tag).endsWith("/deleted");
	}

	public static String deletorTag(String tag) {
		return localTag(tag) + "/deleted" + tagOrigin(tag);
	}

	public static String deletedTag(String deletor) {
		return localTag(deletor).substring("/deleted".length()) + tagOrigin(deletor);
	}

	interface ExpBackoff {
		Instant fetch(int skip, int size, Instant after) throws RetryableException;
	}

}
