package ar.edu.um.progii.catalog.shared.infrastructure.web.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.shared.application.exception.CatalogNotInitializedException;
import jakarta.validation.constraints.Positive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

// Todavía no hay controladores reales: este controlador de prueba provoca cada error
@Import({TestcontainersConfiguration.class, GlobalExceptionHandlerTest.ErrorTriggeringController.class})
@SpringBootTest
class GlobalExceptionHandlerTest {

	@RestController
	static class ErrorTriggeringController {

		@GetMapping("/test-errors/page")
		String getPage(@RequestParam @Positive(message = "must be positive") Integer size) {

			return "page of " + size;

		}

		@GetMapping("/test-errors/not-initialized")
		String failWithCatalogNotInitialized() {

			throw new CatalogNotInitializedException();

		}

		@GetMapping("/test-errors/unexpected")
		String failUnexpectedly() {

			throw new IllegalStateException("Catedra answered 502: upstream secret detail");

		}
	}

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeEach
	void buildMockMvc() {

		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

	}

	@Test
	void answersValidationErrorWithTheInvalidParameter() throws Exception {

		mockMvc.perform(get("/test-errors/page").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.detail").value("size: must be positive"));

	}

	@Test
	void answersValidationErrorWhenAParameterHasTheWrongType() throws Exception {

		mockMvc.perform(get("/test-errors/page").param("size", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.detail").value(containsString("size")));

	}

	@Test
	void answersValidationErrorWhenAParameterIsMissing() throws Exception {

		mockMvc.perform(get("/test-errors/page"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.detail").value(containsString("size")));

	}

	@Test
	void answersCatalogNotInitialized() throws Exception {

		mockMvc.perform(get("/test-errors/not-initialized"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CATALOG_NOT_INITIALIZED"));

	}

	@Test
	void hidesTheCauseOfAnUnexpectedError() throws Exception {

		mockMvc.perform(get("/test-errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("Unexpected error"))
				.andExpect(content().string(not(containsString("upstream secret detail"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));

	}

	// Una ruta inexistente no cae en el 500
	@Test
	void keepsNotFoundForAnUnknownRoute() throws Exception {

		mockMvc.perform(get("/test-errors/missing-route"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

	}
}
