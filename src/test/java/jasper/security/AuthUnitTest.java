package jasper.security;

import jasper.domain.Ref;
import jasper.domain.User;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthUnitTest {

	Auth getAuth(User user, String ...roles) {
		var a = new Auth();
		a.userTag = user.getTag();
		a.user = Optional.of(user);
		a.roles = new HashSet<>(List.of(roles));
		return a;
	}

	User getUser(String ...tags) {
		var u = new User();
		u.setTag("+user/test");
		u.setReadAccess(new ArrayList<>(List.of(tags)));
		u.setWriteAccess(new ArrayList<>(List.of(tags)));
		u.setTagReadAccess(new ArrayList<>(List.of(tags)));
		u.setTagWriteAccess(new ArrayList<>(List.of(tags)));
		return u;
	}

	Ref getRef(String ...tags) {
		var r = new Ref();
		r.setUrl("https://jasperkm.info/");
		r.setTags(new ArrayList<>(List.of(tags)));
		return r;
	}

	RefRepository getRepo(Ref ...refs) {
		var mock = mock(RefRepository.class);
		when(mock.findOneByUrlAndOrigin(anyString(), anyString()))
			.thenReturn(Optional.empty());
		for (var ref : refs) {
			when(mock.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin()))
				.thenReturn(Optional.of(ref));
		}
		return mock;
	}

	@Test
	void testCanReadRef_Public() {
		var auth = getAuth(getUser());
		var ref = getRef("public");

		assertThat(auth.canReadRef(ref))
			.isTrue();
	}

	@Test
	void testCanReadRef_NonPublicFailed() {
		var auth = getAuth(getUser());
		var ref = getRef();

		assertThat(auth.canReadRef(ref))
			.isFalse();
	}

	@Test
	void testCanReadRef_ReadAccess() {
		var user = getUser();
		user.getReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_VIEWER");
		var ref = getRef("+custom");

		assertThat(auth.canReadRef(ref))
			.isTrue();
	}

	@Test
	void testCanReadRef_WriteAccessFailed() {
		var user = getUser();
		user.getWriteAccess().add("+custom");
		var auth = getAuth(user, "ROLE_VIEWER");
		var ref = getRef("+custom");

		assertThat(auth.canReadRef(ref))
			.isFalse();
	}

	@Test
	void testCanReadRef_TagReadAccessFailed() {
		var user = getUser();
		user.getTagReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_VIEWER");
		var ref = getRef("+custom");

		assertThat(auth.canReadRef(ref))
			.isFalse();
	}

	@Test
	void testCanReadRef_TagWriteAccessFailed() {
		var user = getUser();
		user.getTagWriteAccess().add("+custom");
		var auth = getAuth(user, "ROLE_VIEWER");
		var ref = getRef("+custom");

		assertThat(auth.canReadRef(ref))
			.isFalse();
	}

	@Test
	void testCanWriteRef_WriteAccess() {
		var user = getUser();
		user.getWriteAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canWriteRef(ref))
			.isTrue();
	}

	@Test
	void testCanWriteRef_UserTag() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+user/test");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canWriteRef(ref))
			.isTrue();
	}

	@Test
	void testCanWriteRef_ReadAccessFailed() {
		var user = getUser();
		user.getReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canWriteRef(ref))
			.isFalse();
	}

	@Test
	void testCanWriteRef_TagReadAccessFailed() {
		var user = getUser();
		user.getTagReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canWriteRef(ref))
			.isFalse();
	}

	@Test
	void testCanWriteRef_TagWriteAccessFailed() {
		var user = getUser();
		user.getTagWriteAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canWriteRef(ref))
			.isFalse();
	}

	@Test
	void testCanWriteRef_LockedFailed() {
		var user = getUser();
		user.getWriteAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+custom", "locked");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canWriteRef(ref))
			.isFalse();
	}

	@Test
	void testCanAddTag_Public() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("custom"))
			.isTrue();
	}

	@Test
	void testCanAddTag_PrivateFailed() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("_custom"))
			.isFalse();
	}

	@Test
	void testCanAddTag_PrivateTagReadAccess() {
		var user = getUser();
		user.getTagReadAccess().add("_custom");
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("_custom"))
			.isTrue();
	}

	@Test
	void testCanAddTag_PrivateTagWriteAccessFailed() {
		var user = getUser();
		user.getTagWriteAccess().add("_custom");
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("_custom"))
			.isFalse();
	}

	@Test
	void testCanAddTag_ProtectedTagReadAccess() {
		var user = getUser();
		user.getTagReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("+custom"))
			.isTrue();
	}

	@Test
	void testCanAddTag_ProtectedTagWriteAccessFailed() {
		var user = getUser();
		user.getTagWriteAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("+custom"))
			.isFalse();
	}

	@Test
	void testCanAddTag_ViewerFailed() {
		var user = getUser();
		user.getTagWriteAccess().add("custom");
		var auth = getAuth(user, "ROLE_VIEWER");

		assertThat(auth.canAddTag("custom"))
			.isFalse();
	}

	@Test
	void testCanAddTag_UserTag() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_USER");

		assertThat(auth.canAddTag("+user/test"))
			.isTrue();
	}

	@Test
	void testCanTag_Public() {
		var user = getUser();
		user.getTagWriteAccess().add("custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+user/test");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("custom", ref.getUrl(), ref.getOrigin()))
			.isTrue();
	}

	@Test
	void testCanTag_PublicFailed() {
		var user = getUser();
		user.getTagReadAccess().add("custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef();
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("custom", ref.getUrl(), ref.getOrigin()))
			.isFalse();
	}

	@Test
	void testCanTag_Protected() {
		var user = getUser();
		user.getTagReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+user/test");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("+custom", ref.getUrl(), ref.getOrigin()))
			.isTrue();
	}

	@Test
	void testCanTag_Private() {
		var user = getUser();
		user.getTagReadAccess().add("_custom");
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+user/test");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("_custom", ref.getUrl(), ref.getOrigin()))
			.isTrue();
	}

	@Test
	void testCanTag_PrivateFailed() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_USER");
		var ref = getRef("+user/test");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("_custom", ref.getUrl(), ref.getOrigin()))
			.isFalse();
	}

	@Test
	void testCanTag_EditorPublic() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_EDITOR");
		var ref = getRef("public");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("custom", ref.getUrl(), ref.getOrigin()))
			.isTrue();
	}

	@Test
	void testCanTag_EditorMakePublicFailed() {
		var user = getUser();
		user.getReadAccess().add("custom");
		var auth = getAuth(user, "ROLE_EDITOR");
		var ref = getRef("custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("public", ref.getUrl(), ref.getOrigin()))
			.isFalse();
	}

	@Test
	void testCanTag_EditorMakeLockedFailed() {
		var user = getUser();
		user.getReadAccess().add("custom");
		var auth = getAuth(user, "ROLE_EDITOR");
		var ref = getRef("custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("locked", ref.getUrl(), ref.getOrigin()))
			.isFalse();
	}

	@Test
	void testCanTag_EditorProtectedFailed() {
		var user = getUser();
		user.getTagReadAccess().add("+custom");
		var auth = getAuth(user, "ROLE_EDITOR");
		var ref = getRef("custom");
		auth.refRepository = getRepo(ref);

		assertThat(auth.canTag("locked", ref.getUrl(), ref.getOrigin()))
			.isFalse();
	}

	@Test
	void testCanReadTag_Private() {
		var user = getUser();
		user.getTagReadAccess().add("_custom");
		var auth = getAuth(user, "ROLE_VIEWER");

		assertThat(auth.canReadTag("_custom"))
			.isTrue();
	}

	@Test
	void testCanReadTag_PrivateFailed() {
		var user = getUser();
		var auth = getAuth(user, "ROLE_VIEWER");

		assertThat(auth.canReadTag("_custom"))
			.isFalse();
	}
}
