package ar.edu.um.progii.catalog.category.domain.model;

// Una categoría de profesionales del catálogo de la cátedra (por ejemplo, clínica médica)
public class ProfessionalCategory {

	// Id que asigna la cátedra
	private final long id;
	private final String name;
	// Opcional: la cátedra puede no mandarla
	private final String description;
	private final boolean enabled;

	public ProfessionalCategory(long id, String name, String description, boolean enabled) {

		this.id = id;
		this.name = validateName(name);
		this.description = description;
		this.enabled = enabled;

	}

	private static String validateName(String name) {

		if (name == null) {
			throw new IllegalArgumentException("Name is mandatory");
		}
		return name;

	}
}
