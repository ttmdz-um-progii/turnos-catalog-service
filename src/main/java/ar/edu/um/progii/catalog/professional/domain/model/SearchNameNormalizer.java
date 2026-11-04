package ar.edu.um.progii.catalog.professional.domain.model;

import java.text.Normalizer;
import java.util.Locale;

// Forma de un nombre que se usa para buscar sin distinguir mayúsculas ni acentos: "Ana  Gómez " → "ana gomez".
// Se aplica igual al nombre que se guarda y al texto que se busca, así los dos se comparan en la misma forma
public final class SearchNameNormalizer {

	private SearchNameNormalizer() {

	}

	public static String normalize(String text) {

		if (text == null) {
			throw new IllegalArgumentException("Text to normalize is mandatory");
		}
		// NFD separa cada letra de su acento ("ó" queda como "o" más la marca del acento), y \p{M} borra esas marcas
		String textWithoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String lowerCaseText = textWithoutAccents.toLowerCase(Locale.ROOT);
		return lowerCaseText.strip().replaceAll("\\s+", " ");

	}
}
