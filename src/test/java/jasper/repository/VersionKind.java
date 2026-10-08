package jasper.repository;

import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.IngestExt;
import jasper.component.IngestPlugin;
import jasper.component.IngestTemplate;
import jasper.component.IngestUser;
import jasper.component.Messages;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.Tag;
import jasper.errors.NotFoundException;
import jasper.repository.filter.TagFilter;
import jasper.service.ExtService;
import jasper.service.PluginService;
import jasper.service.TemplateService;
import jasper.service.UserService;
import org.mockito.verification.VerificationMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jasper.component.Replicator.deletorTag;
import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * One versioned entity type (Ref, Ext, User, Plugin or Template) so the same
 * delete/push/prune scenarios can be run against every type.
 * A key is a URL for Refs and a local tag for the others.
 */
public abstract class VersionKind {
	public final String name;
	public final String key;

	VersionKind(String name, String key) {
		this.name = name;
		this.key = key;
	}

	@Override
	public String toString() {
		return name;
	}

	/** Push a version with content. */
	public abstract void push(String origin, Instant modified, String title);

	/** Push a version with every non-key field empty. */
	public abstract void pushBlank(String origin, Instant modified);

	/** Push a delete notice: a deletor tag, or a plugin/delete Ref. */
	public abstract void pushDeleteNotice(String origin, Instant modified);

	/** Create through ingest, which sets modified. */
	public abstract void create(String origin, String title);

	/** Create a blank item through ingest. */
	public abstract void createBlank(String origin);

	public abstract void delete(String origin);

	/** Delete the deletor tag directly. Not supported for Refs. */
	public abstract void deleteDeletor(String origin);

	public abstract boolean hasDeletor();

	/** Single lookup of the current version (archive aware). */
	public abstract Optional<?> current(String origin);

	/** Single lookup through the service, throws {@link NotFoundException}. */
	public abstract Object get(String origin);

	/** Title (or name) of the current version, read without archive filtering. */
	public abstract Optional<String> latestTitle(String origin);

	public abstract List<Instant> versions(String origin);

	public abstract long count(String origin);

	public abstract long countDeletor(String origin);

	/** Page total through the service. Not supported for Refs. */
	public abstract long pageCount();

	/** Verify the delete notice a regular server sends, if any. */
	public abstract void verifyDeleteNotice(Messages messages, VerificationMode mode);

	public abstract void setClock(Clock clock);

	static <T extends Tag> long countTag(QualifiedTagMixin<T> repo, String tag, String origin) {
		Specification<T> spec = (root, query, cb) -> cb.and(
			cb.equal(root.get("tag"), tag),
			cb.equal(root.get("origin"), origin));
		return repo.count(spec);
	}

	static <T extends Tag> List<Instant> tagVersions(QualifiedTagMixin<T> repo, String tag, String origin) {
		Specification<T> spec = (root, query, cb) -> cb.and(
			cb.equal(root.get("tag"), tag),
			cb.equal(root.get("origin"), origin));
		return repo.findAll(spec).stream().map(Tag::getModified).sorted().toList();
	}

	static void clock(Object ingest, Clock clock) {
		Object target = getUltimateTargetObject(ingest);
		setField(target, "ensureUniqueModifiedClock", clock);
	}

	public static List<VersionKind> all(
		Ingest ingest, RefRepository refRepository,
		IngestExt ingestExt, ExtRepository extRepository, ExtService extService,
		IngestUser ingestUser, UserRepository userRepository, UserService userService,
		IngestPlugin ingestPlugin, PluginRepository pluginRepository, PluginService pluginService,
		IngestTemplate ingestTemplate, TemplateRepository templateRepository, TemplateService templateService,
		ConfigCache configCache
	) {
		return List.of(
			new RefKind(ingest, refRepository),
			new TagKind<Ext>("Ext", "test", extRepository, ingestExt) {
				Ext make(String tag, String origin, Instant modified, String title) {
					var ext = new Ext();
					ext.setTag(tag);
					ext.setOrigin(origin);
					ext.setName(title);
					ext.setModified(modified);
					return ext;
				}
				void doPush(Ext e) { ingestExt.push("", e, false, false); }
				void doCreate(Ext e) { ingestExt.create(e); }
				void doDelete(String qt) { ingestExt.delete(qt); }
				Optional<Ext> doCurrent(String qt) { return ingestExt.current(qt); }
				Object doGet(String qt) { return extService.get(qt); }
				String title(Ext e) { return e.getName(); }
				public long pageCount() {
					return extService.page(TagFilter.builder().build(), PageRequest.of(0, 100)).getTotalElements();
				}
				public void verifyDeleteNotice(Messages messages, VerificationMode mode) {
					// Regular servers do not send a delete notice for Exts
				}
			},
			new TagKind<User>("User", "+user/test", userRepository, ingestUser) {
				User make(String tag, String origin, Instant modified, String title) {
					var user = new User();
					user.setTag(tag);
					user.setOrigin(origin);
					user.setName(title);
					user.setModified(modified);
					return user;
				}
				void doPush(User u) { ingestUser.push(u); }
				void doCreate(User u) { ingestUser.create(u); }
				void doDelete(String qt) { ingestUser.delete(qt); }
				Optional<User> doCurrent(String qt) { return ingestUser.current(qt); }
				Object doGet(String qt) {
					configCache.clearUserCache();
					return userService.get(qt);
				}
				String title(User u) { return u.getName(); }
				public long pageCount() {
					configCache.clearUserCache();
					return userService.page(TagFilter.builder().build(), PageRequest.of(0, 100)).getTotalElements();
				}
				public void verifyDeleteNotice(Messages messages, VerificationMode mode) {
					verify(messages, mode).deleteUser(anyString());
				}
			},
			new TagKind<Plugin>("Plugin", "plugin/test", pluginRepository, ingestPlugin) {
				Plugin make(String tag, String origin, Instant modified, String title) {
					var plugin = new Plugin();
					plugin.setTag(tag);
					plugin.setOrigin(origin);
					plugin.setName(title);
					plugin.setModified(modified);
					return plugin;
				}
				void doPush(Plugin p) { ingestPlugin.push(p); }
				void doCreate(Plugin p) { ingestPlugin.create(p); }
				void doDelete(String qt) { ingestPlugin.delete(qt); }
				Optional<Plugin> doCurrent(String qt) { return ingestPlugin.current(qt); }
				Object doGet(String qt) {
					configCache.clearPluginCache();
					return pluginService.get(qt);
				}
				String title(Plugin p) { return p.getName(); }
				public long pageCount() {
					configCache.clearPluginCache();
					return pluginService.page(TagFilter.builder().build(), PageRequest.of(0, 100)).getTotalElements();
				}
				public void verifyDeleteNotice(Messages messages, VerificationMode mode) {
					verify(messages, mode).deletePlugin(anyString());
				}
			},
			new TagKind<Template>("Template", "test", templateRepository, ingestTemplate) {
				Template make(String tag, String origin, Instant modified, String title) {
					var template = new Template();
					template.setTag(tag);
					template.setOrigin(origin);
					template.setName(title);
					template.setModified(modified);
					return template;
				}
				void doPush(Template t) { ingestTemplate.push(t); }
				void doCreate(Template t) { ingestTemplate.create(t); }
				void doDelete(String qt) { ingestTemplate.delete(qt); }
				Optional<Template> doCurrent(String qt) { return ingestTemplate.current(qt); }
				Object doGet(String qt) {
					configCache.clearTemplateCache();
					return templateService.get(qt);
				}
				String title(Template t) { return t.getName(); }
				public long pageCount() {
					configCache.clearTemplateCache();
					return templateService.page(TagFilter.builder().build(), PageRequest.of(0, 100)).getTotalElements();
				}
				public void verifyDeleteNotice(Messages messages, VerificationMode mode) {
					verify(messages, mode).deleteTemplate(anyString());
				}
			}
		);
	}

	static class RefKind extends VersionKind {
		final Ingest ingest;
		final RefRepository refRepository;

		RefKind(Ingest ingest, RefRepository refRepository) {
			super("Ref", "https://www.example.com/");
			this.ingest = ingest;
			this.refRepository = refRepository;
		}

		Ref make(String origin, Instant modified, String title, String ...tags) {
			var ref = new Ref();
			ref.setUrl(key);
			ref.setOrigin(origin);
			ref.setTitle(title);
			if (tags.length > 0) ref.setTags(new ArrayList<>(List.of(tags)));
			ref.setModified(modified);
			return ref;
		}

		public void push(String origin, Instant modified, String title) {
			ingest.push("", make(origin, modified, title, "public"), false, false);
		}

		public void pushBlank(String origin, Instant modified) {
			ingest.push("", make(origin, modified, null), false, false);
		}

		public void pushDeleteNotice(String origin, Instant modified) {
			ingest.push("", make(origin, modified, null, "internal", "plugin/delete"), false, false);
		}

		public void create(String origin, String title) {
			ingest.create("", make(origin, null, title, "public"));
		}

		public void createBlank(String origin) {
			ingest.create("", make(origin, null, null));
		}

		public void delete(String origin) {
			ingest.delete("", key, origin);
		}

		public void deleteDeletor(String origin) {
			throw new UnsupportedOperationException();
		}

		public boolean hasDeletor() {
			return false;
		}

		public Optional<?> current(String origin) {
			return ingest.current(key, origin);
		}

		public Object get(String origin) {
			return ingest.current(key, origin).orElseThrow(() -> new NotFoundException("Ref"));
		}

		public Optional<String> latestTitle(String origin) {
			return refRepository.findOneByUrlAndOrigin(key, origin).map(Ref::getTitle);
		}

		public List<Instant> versions(String origin) {
			return refRepository.findAll(isUrl(key).and(isOrigin(origin))).stream()
				.map(Ref::getModified).sorted().toList();
		}

		public long count(String origin) {
			return refRepository.count(isUrl(key).and(isOrigin(origin)));
		}

		public long countDeletor(String origin) {
			return 0;
		}

		public long pageCount() {
			throw new UnsupportedOperationException();
		}

		public void verifyDeleteNotice(Messages messages, VerificationMode mode) {
			verify(messages, mode).deleteRef(any());
		}

		public void setClock(Clock clock) {
			clock(ingest, clock);
		}
	}

	abstract static class TagKind<T extends Tag> extends VersionKind {
		final QualifiedTagMixin<T> repo;
		final Object ingest;

		TagKind(String name, String key, QualifiedTagMixin<T> repo, Object ingest) {
			super(name, key);
			this.repo = repo;
			this.ingest = ingest;
		}

		abstract T make(String tag, String origin, Instant modified, String title);
		abstract void doPush(T entity);
		abstract void doCreate(T entity);
		abstract void doDelete(String qualifiedTag);
		abstract Optional<T> doCurrent(String qualifiedTag);
		abstract Object doGet(String qualifiedTag);
		abstract String title(T entity);

		public void push(String origin, Instant modified, String title) {
			doPush(make(key, origin, modified, title));
		}

		public void pushBlank(String origin, Instant modified) {
			doPush(make(key, origin, modified, null));
		}

		public void pushDeleteNotice(String origin, Instant modified) {
			doPush(make(deletorTag(key), origin, modified, null));
		}

		public void create(String origin, String title) {
			doCreate(make(key, origin, null, title));
		}

		public void createBlank(String origin) {
			doCreate(make(key, origin, null, null));
		}

		public void delete(String origin) {
			doDelete(key + origin);
		}

		public void deleteDeletor(String origin) {
			doDelete(deletorTag(key) + origin);
		}

		public boolean hasDeletor() {
			return true;
		}

		public Optional<?> current(String origin) {
			return doCurrent(key + origin);
		}

		public Object get(String origin) {
			return doGet(key + origin);
		}

		public Optional<String> latestTitle(String origin) {
			return repo.findOneByQualifiedTag(key + origin).map(this::title);
		}

		public List<Instant> versions(String origin) {
			return tagVersions(repo, key, origin);
		}

		public long count(String origin) {
			return countTag(repo, key, origin);
		}

		public long countDeletor(String origin) {
			return countTag(repo, localDeletor(), origin);
		}

		String localDeletor() {
			return deletorTag(key);
		}

		public void setClock(Clock clock) {
			clock(ingest, clock);
		}
	}
}
