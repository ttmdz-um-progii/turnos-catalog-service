package ar.edu.um.progii.catalog.professional.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProfessionalTest {

	@Test
	void searchNameIsTheNormalizedFirstAndLastName() {

		Professional professional = new Professional(15, 1, "Ana", "Gómez", true);

		assertThat(professional.getSearchName()).isEqualTo("ana gomez");

	}

	@Test
	void rejectsNullFirstName() {

		assertThatThrownBy(() -> new Professional(15, 1, null, "Gómez", true))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullLastName() {

		assertThatThrownBy(() -> new Professional(15, 1, "Ana", null, true))
				.isInstanceOf(IllegalArgumentException.class);

	}
}
