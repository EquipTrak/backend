package com.equiptrack.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com a configuração padrão, o que não é da API não pode ficar aberto a qualquer um. */
@SpringBootTest
@AutoConfigureMockMvc
class ExposicaoOperacionalTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void documentacaoOpenApiNaoEPublicaPorPadrao() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
	}

	@Test
	void healthEPublicoESemDetalhes() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void outrosEndpointsDoActuatorNaoEstaoAbertos() throws Exception {
		mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
	}

}
