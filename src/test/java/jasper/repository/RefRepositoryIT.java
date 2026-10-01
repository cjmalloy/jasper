package jasper.repository;

import jasper.IntegrationTest;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class RefRepositoryIT {

	@Autowired
	RefRepository refRepository;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testDropMetadata_MarksRefWithoutRegen() {
		var ref = new Ref();
		ref.setUrl("https://www.example.com/");
		ref.setOrigin("");
		ref.setMetadata(Metadata.builder().build());
		refRepository.save(ref);

		refRepository.dropMetadata("");

		var updated = refRepository.findOneByUrlAndOrigin("https://www.example.com/", "").orElseThrow();
		assertThat(updated.getMetadata()).isNotNull();
		assertThat(updated.getMetadata().isRegen()).isTrue();
	}
}
