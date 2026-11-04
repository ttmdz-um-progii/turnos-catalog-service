package ar.edu.um.progii.catalog.category.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProfessionalCategoryTest {

	@Test
	void rejectsNullName() {

		assertThatThrownBy(() -> new ProfessionalCategory(1, null, "Atención clínica general", true))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void acceptsNullDescription() {

		assertThatCode(() -> new ProfessionalCategory(1, "Clínica médica", null, true)).doesNotThrowAnyException();

	}
}
