package ar.edu.um.progii.catalog.professional.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchNameNormalizerTest {

	@Test
	void removesAccentsAndLowersCase() {

		assertThat(SearchNameNormalizer.normalize("Ana Gómez")).isEqualTo("ana gomez");

	}

	@Test
	void turnsEnyeIntoN() {

		assertThat(SearchNameNormalizer.normalize("Peña")).isEqualTo("pena");

	}

	@Test
	void removesEveryKindOfAccent() {

		assertThat(SearchNameNormalizer.normalize("ÁÉÍÓÚ àü")).isEqualTo("aeiou au");

	}

	@Test
	void stripsEdgeSpacesAndCollapsesRepeatedOnes() {

		assertThat(SearchNameNormalizer.normalize("  Ana   Gómez ")).isEqualTo("ana gomez");

	}

	@Test
	void rejectsNullText() {

		assertThatThrownBy(() -> SearchNameNormalizer.normalize(null)).isInstanceOf(IllegalArgumentException.class);

	}
}
