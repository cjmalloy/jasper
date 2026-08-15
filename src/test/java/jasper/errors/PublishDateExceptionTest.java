package jasper.errors;

import jasper.domain.Ref;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PublishDateExceptionTest {

	@Test
	void includesSourceAndResponsePublicationDates() {
		var source = Ref.from("ai:a6ac75b8-ce69-484a-9fe7-5b28e35f4c85", "");
		source.setPublished(Instant.parse("2025-03-22T21:14:04Z"));
		var response = Ref.from("comment:cdb3412c-df14-48dd-b0de-969ed0931138", "");
		response.setPublished(Instant.parse("2025-03-22T21:14:39Z"));

		assertThat(new PublishDateException(response, source))
			.hasMessage("""
				Source ai:a6ac75b8-ce69-484a-9fe7-5b28e35f4c85 (2025-03-22T21:14:04Z) must predate response comment:cdb3412c-df14-48dd-b0de-969ed0931138 (2025-03-22T21:14:39Z)""");
	}
}
