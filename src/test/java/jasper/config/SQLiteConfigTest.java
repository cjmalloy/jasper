package jasper.config;

import org.junit.jupiter.api.Test;

import static jasper.config.SQLiteConfig.isoDurationSeconds;
import static org.assertj.core.api.Assertions.assertThat;

public class SQLiteConfigTest {

	@Test
	void testIsoDurationSeconds() {
		assertThat(isoDurationSeconds("pt10m25s")).isEqualTo(625.0);
		assertThat(isoDurationSeconds("PT1.5S")).isEqualTo(1.5);
		assertThat(isoDurationSeconds("p1w2d")).isEqualTo(9 * 86400.0);
		assertThat(isoDurationSeconds("P1Y2M3W4DT5H6M7.5S")).isEqualTo(38919967.5);
	}

	@Test
	void testIsoDurationSecondsInvalid() {
		assertThat(isoDurationSeconds(null)).isNull();
		assertThat(isoDurationSeconds("")).isNull();
		assertThat(isoDurationSeconds("p")).isNull();
		assertThat(isoDurationSeconds("pt")).isNull();
		assertThat(isoDurationSeconds("p1dt")).isNull();
		assertThat(isoDurationSeconds("pt1x")).isNull();
		assertThat(isoDurationSeconds("37")).isNull();
	}
}
