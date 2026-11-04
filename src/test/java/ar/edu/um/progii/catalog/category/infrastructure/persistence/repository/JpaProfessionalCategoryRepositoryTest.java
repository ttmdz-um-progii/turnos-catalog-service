package ar.edu.um.progii.catalog.category.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.category.infrastructure.persistence.entity.ProfessionalCategoryEntity;

// Cada test hace rollback al terminar (@Transactional), así no deja filas para los demás
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaProfessionalCategoryRepositoryTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaProfessionalCategoryRepository jpaProfessionalCategoryRepository;

	@Test
	void readsACategoryWrittenWithSql() {

		jdbcTemplate.update("INSERT INTO professional_category (id, name, description, enabled) VALUES (?, ?, ?, ?)",
				1L, "Clínica médica", "Atención clínica general", true);

		ProfessionalCategoryEntity category = jpaProfessionalCategoryRepository.findById(1L).orElseThrow();

		assertThat(category.getId()).isEqualTo(1L);
		assertThat(category.getName()).isEqualTo("Clínica médica");
		assertThat(category.getDescription()).isEqualTo("Atención clínica general");
		assertThat(category.isEnabled()).isTrue();

	}
}
