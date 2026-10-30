package jasper.component.channel;

import jasper.domain.Ref;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import jasper.service.dto.RefDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

import static jasper.component.Messages.originHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.messaging.support.MessageBuilder.createMessage;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchTest {

	@InjectMocks
	Watch watch;

	@Mock
	RefRepository refRepository;

	RefDto ref(String origin, String ...tags) {
		var ref = new RefDto();
		ref.setUrl("https://example.com/" + origin);
		ref.setOrigin(origin);
		ref.setTags(List.of(tags));
		return ref;
	}

	void send(RefDto ref) {
		watch.handleRefUpdate(createMessage(ref, originHeaders(ref.getOrigin())));
	}

	@Test
	void testExactOrigin() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.origin", "+plugin/cron", seen::add);

		send(ref("@tenant.origin", "+plugin/cron"));
		send(ref("@tenant.other", "+plugin/cron"));

		assertThat(seen).extracting(HasTags::getOrigin).containsExactly("@tenant.origin");
	}

	@Test
	void testWildcardOrigin() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);

		send(ref("@tenant.origin", "+plugin/cron"));
		send(ref("@tenant.other", "+plugin/cron"));
		send(ref("@other.origin", "+plugin/cron"));
		send(ref("@tenant.untagged", "plugin/other"));

		assertThat(seen).extracting(HasTags::getOrigin).containsExactly("@tenant.origin", "@tenant.other");
	}

	@Test
	void testAllOrigins() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@*", "+plugin/cron", seen::add);

		send(ref("@tenant.origin", "+plugin/cron"));
		send(ref("@other", "+plugin/cron"));

		assertThat(seen).extracting(HasTags::getOrigin).containsExactly("@tenant.origin", "@other");
	}

	@Test
	void testDefaultOriginOnly() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("", "+plugin/cron", seen::add);

		send(ref("", "+plugin/cron"));
		send(ref("@other", "+plugin/cron"));

		assertThat(seen).extracting(HasTags::getOrigin).containsExactly("");
	}

	@Test
	void testOverlappingSelectorsNotifyOnce() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);
		watch.addWatch("@tenant.origin", "+plugin/cron", seen::add);

		send(ref("@tenant.origin", "+plugin/cron"));

		assertThat(seen).hasSize(1);
	}

	@Test
	void testUntaggedRefNotifiedOnceAfterTagRemoved() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);

		send(ref("@tenant.origin", "+plugin/cron"));
		send(ref("@tenant.origin"));
		send(ref("@tenant.origin"));

		assertThat(seen).hasSize(2);
	}

	@Test
	void testRemoveWatch() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);
		watch.removeWatch("@tenant.*", "+plugin/cron");

		send(ref("@tenant.origin", "+plugin/cron"));

		assertThat(seen).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testScannedRefNotifiedWhenTagRemoved() {
		var existing = Ref.from("https://example.com/@tenant.origin", "@tenant.origin", "+plugin/cron");
		when(refRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn((Page<Ref>) new PageImpl<>(List.of(existing)))
			.thenReturn(Page.empty());
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);
		watch.init();

		send(ref("@tenant.origin"));

		assertThat(seen).hasSize(2);
	}

	@Test
	void testRemovingExactWatchKeepsTrackingForWildcard() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);
		watch.addWatch("@tenant.origin", "+plugin/cron", seen::add);
		send(ref("@tenant.origin", "+plugin/cron"));

		watch.removeWatch("@tenant.origin", "+plugin/cron");
		send(ref("@tenant.origin"));

		assertThat(seen).hasSize(2);
	}

	@Test
	void testReplacingWatchKeepsTracking() {
		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.origin", "+plugin/cron", seen::add);
		send(ref("@tenant.origin", "+plugin/cron"));

		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);
		watch.removeWatch("@tenant.origin", "+plugin/cron");
		send(ref("@tenant.origin"));

		assertThat(seen).hasSize(2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testReAddWatchAfterRemoveRescans() {
		when(refRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
		watch.addWatch("@tenant.*", "+plugin/cron", r -> {});
		watch.init();
		watch.removeWatch("@tenant.*", "+plugin/cron");

		watch.addWatch("@tenant.*", "+plugin/cron", r -> {});

		verify(refRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testAddWatchBeforeReadyDoesNotScan() {
		watch.addWatch("@tenant.origin", "+plugin/cron", r -> {});

		verify(refRepository, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testAddWatchAfterReadyScansExistingRefs() {
		var existing = Ref.from("https://example.com/feed", "@tenant.origin", "+plugin/cron", "plugin/script/feed");
		when(refRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn((Page<Ref>) new PageImpl<>(List.of(existing)))
			.thenReturn(Page.empty());
		watch.init();

		var seen = new ArrayList<HasTags>();
		watch.addWatch("@tenant.*", "+plugin/cron", seen::add);

		assertThat(seen).containsExactly(existing);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testReAddWatchAfterReadyDoesNotRescan() {
		watch.addWatch("@tenant.*", "+plugin/cron", r -> {});
		when(refRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
		watch.init();

		watch.addWatch("@tenant.*", "+plugin/cron", r -> {});

		verify(refRepository).findAll(any(Specification.class), any(Pageable.class));
	}
}
