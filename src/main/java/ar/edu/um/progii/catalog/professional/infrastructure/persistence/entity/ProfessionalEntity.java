package ar.edu.um.progii.catalog.professional.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "professional")
@NoArgsConstructor
public class ProfessionalEntity {

	// Id de la cátedra: sin @GeneratedValue
	@Id
	private Long id;
	// Id plano y no @ManyToOne: la categoría es de otro módulo
	private Long categoryId;
	private String firstName;
	private String lastName;
	private String searchName;
	private boolean enabled;
}
