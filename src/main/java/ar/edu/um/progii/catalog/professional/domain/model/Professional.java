package ar.edu.um.progii.catalog.professional.domain.model;

import lombok.Builder;
import lombok.Getter;

// Un profesional del catálogo de la cátedra. Sus horarios semanales son WeeklySchedule, unidos por professionalId
@Getter
public class Professional {

	// Id que asigna la cátedra
	private final long id;
	private final long categoryId;
	private final String firstName;
	private final String lastName;
	// Nombre y apellido normalizados para buscar (SearchNameNormalizer)
	private final String searchName;
	private final boolean enabled;

	@Builder
	public Professional(long id, long categoryId, String firstName, String lastName, boolean enabled) {

		this.id = id;
		this.categoryId = categoryId;
		this.firstName = validateFirstName(firstName);
		this.lastName = validateLastName(lastName);
		this.searchName = SearchNameNormalizer.normalize(firstName + " " + lastName);
		this.enabled = enabled;

	}

	private static String validateFirstName(String firstName) {

		if (firstName == null) {
			throw new IllegalArgumentException("First name is mandatory");
		}
		return firstName;

	}

	private static String validateLastName(String lastName) {

		if (lastName == null) {
			throw new IllegalArgumentException("Last name is mandatory");
		}
		return lastName;

	}
}
