package ar.edu.um.progii.catalog.category.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "professional_category")
@NoArgsConstructor
public class ProfessionalCategoryEntity {

	// Id de la cátedra: sin @GeneratedValue
	@Id
	private Long id;
	private String name;
	private String description;
	private boolean enabled;
}
