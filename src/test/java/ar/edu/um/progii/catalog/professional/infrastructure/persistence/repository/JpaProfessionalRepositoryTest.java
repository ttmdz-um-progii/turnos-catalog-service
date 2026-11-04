package ar.edu.um.progii.catalog.professional.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.professional.infrastructure.persistence.entity.ProfessionalEntity;

// Cada test hace rollback al terminar (@Transactional), así no deja filas para los demás
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaProfessionalRepositoryTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaProfessionalRepository jpaProfessionalRepository;

	@Test
	void readsAProfessionalWrittenWithSql() {

		jdbcTemplate.update("INSERT INTO professional_category (id, name, enabled) VALUES (?, ?, ?)", 1L,
				"Clínica médica", true);
		jdbcTemplate.update(
				"INSERT INTO professional (id, category_id, first_name, last_name, search_name, enabled) VALUES (?, ?, ?, ?, ?, ?)",
				15L, 1L, "Ana", "Gómez", "ana gomez", false);

		ProfessionalEntity professional = jpaProfessionalRepository.findById(15L).orElseThrow();

		assertThat(professional.getId()).isEqualTo(15L);
		assertThat(professional.getCategoryId()).isEqualTo(1L);
		assertThat(professional.getFirstName()).isEqualTo("Ana");
		assertThat(professional.getLastName()).isEqualTo("Gómez");
		assertThat(professional.getSearchName()).isEqualTo("ana gomez");
		assertThat(professional.isEnabled()).isFalse();

	}
}
