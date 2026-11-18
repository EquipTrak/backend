package com.equiptrack.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ligada de propósito (SWAGGER_ATIVO=true), a documentação OpenApi é pública e lista os endpoints. */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
@AutoConfigureMockMvc
class DocumentacaoLigadaTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void documentacaoOpenApiListaOsEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
			.andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
			.andExpect(jsonPath("$.paths['/api/auth/me'].get").exists());
	}

}
