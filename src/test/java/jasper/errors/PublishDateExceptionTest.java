package jasper.errors;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PublishDateExceptionTest {

	@Test
	void includesSourceAndResponsePublicationDates() {
		assertThat(new PublishDateException(
			"comment:cdb3412c-df14-48dd-b0de-969ed0931138",
			Instant.parse("2025-03-22T21:14:39Z"),
			"ai:a6ac75b8-ce69-484a-9fe7-5b28e35f4c85",
			Instant.parse("2025-03-22T21:14:04Z")))
			.hasMessage("""
				Source ai:a6ac75b8-ce69-484a-9fe7-5b28e35f4c85 (2025-03-22T21:14:04Z) must predate response comment:cdb3412c-df14-48dd-b0de-969ed0931138 (2025-03-22T21:14:39Z)""");
	}
}
