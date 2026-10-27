package ar.edu.um.progii.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FlywayMigrationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void appliesBaselineOnStartup() {
		Boolean success = jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

		assertThat(success).isTrue();
	}

}
