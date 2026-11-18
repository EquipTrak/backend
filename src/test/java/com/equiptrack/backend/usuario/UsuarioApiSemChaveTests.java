package com.equiptrack.backend.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem a variável EQUIPTRACK_CHAVE_DESENVOLVEDOR, nenhuma chave abre a gestão de usuários. */
@SpringBootTest(properties = "equiptrack.desenvolvedor.chave=")
@AutoConfigureMockMvc
class UsuarioApiSemChaveTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rotasFicamFechadas() throws Exception {
		mockMvc.perform(get("/api/usuarios").header("X-Chave-Desenvolvedor", ""))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/usuarios").header("X-Chave-Desenvolvedor", "qualquer-coisa"))
			.andExpect(status().isUnauthorized());
	}

}
