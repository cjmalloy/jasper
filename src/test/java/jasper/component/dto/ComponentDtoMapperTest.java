package jasper.component.dto;

import jasper.domain.Metadata;
import jasper.domain.Ref;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentDtoMapperTest {

	ComponentDtoMapper mapper = new ComponentDtoMapperImpl();

	@Test
	void testDomainToDtoKeepsCascadeAndRegen() {
		var ref = new Ref();
		ref.setUrl("https://www.example.com/");
		ref.setMetadata(Metadata.builder().cascade(true).regen(true).build());

		var dto = mapper.domainToDto(ref);

		assertThat(dto.getMetadata().isCascade()).isTrue();
		assertThat(dto.getMetadata().isRegen()).isTrue();
	}
}
