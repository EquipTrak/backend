package com.equiptrack.backend.usuario;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "equiptrack.desenvolvedor.chave=" + UsuarioApiTests.CHAVE)
@AutoConfigureMockMvc
class UsuarioApiTests {

	static final String CHAVE = "chave-de-teste-dos-desenvolvedores";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private SolicitanteRepository solicitanteRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	@Autowired
	private HistoricoEquipamentoRepository historicoRepository;

	@Autowired
	private EquipamentoRepository equipamentoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@BeforeEach
	@AfterEach
	void limpar() {
		historicoRepository.deleteAll();
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
		sessaoRepository.deleteAll();
		solicitanteRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	void semChaveOuComChaveErradaNaoEntra() throws Exception {
		mockMvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/usuarios").header("X-Chave-Desenvolvedor", "chave-errada"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/usuarios").header("X-Chave-Desenvolvedor", "chave-errada")
				.contentType(MediaType.APPLICATION_JSON).content(operador("op@equiptrack.local")))
			.andExpect(status().isUnauthorized());
		assertThat(usuarioRepository.count()).isZero();
	}

	@Test
	void tokenDeAdministradorNaoDaAcesso() throws Exception {
		cadastrar(usuario("admin@equiptrack.local", "ADMINISTRADOR")).andExpect(status().isCreated());
		String token = entrar("admin@equiptrack.local");

		mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden());
	}

	@Test
	void cadastraOperadorQueConsegueEntrar() throws Exception {
		cadastrar(operador("Nova.Operadora@EquipTrack.local"))
			.andExpect(status().isCreated())
			.andExpect(header().exists(HttpHeaders.LOCATION))
			.andExpect(jsonPath("$.email").value("nova.operadora@equiptrack.local"))
			.andExpect(jsonPath("$.perfil").value("OPERADOR"))
			.andExpect(jsonPath("$.ativo").value(true))
			.andExpect(jsonPath("$.solicitante").value(nullValue()))
			.andExpect(jsonPath("$.senha").doesNotExist())
			.andExpect(jsonPath("$.senhaHash").doesNotExist());

		entrar("nova.operadora@equiptrack.local");
	}

	@Test
	void solicitanteExigeOsDadosDeSolicitante() throws Exception {
		cadastrar(usuario("aluno@equiptrack.local", "SOLICITANTE"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.solicitante").exists());

		cadastrar(solicitante("aluno@equiptrack.local", "MAT-001"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.solicitante.tipo").value("ALUNO"))
			.andExpect(jsonPath("$.solicitante.matricula").value("MAT-001"));
		assertThat(solicitanteRepository.findByMatricula("MAT-001")).isPresent();
	}

	@Test
	void operadorNaoRecebeDadosDeSolicitante() throws Exception {
		String corpo = """
				{"nome":"Op","email":"op@equiptrack.local","senha":"senha-forte-1","perfil":"OPERADOR",
				 "solicitante":{"tipo":"ALUNO","matricula":"MAT-009"}}""";
		cadastrar(corpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.solicitante").exists());
	}

	@Test
	void recusaEmailEMatriculaRepetidos() throws Exception {
		cadastrar(solicitante("aluno@equiptrack.local", "MAT-001")).andExpect(status().isCreated());

		cadastrar(operador("ALUNO@equiptrack.local"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.email").exists());
		cadastrar(solicitante("outro@equiptrack.local", "MAT-001"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos['solicitante.matricula']").exists());
	}

	@Test
	void validaOsCampos() throws Exception {
		cadastrar("""
				{"nome":"","email":"nao-e-email","senha":"curta","perfil":"OPERADOR"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.nome").exists())
			.andExpect(jsonPath("$.campos.email").exists())
			.andExpect(jsonPath("$.campos.senha").exists());
		cadastrar("""
				{"nome":"X","email":"x@equiptrack.local","senha":"senha-forte-1","perfil":"DESENVOLVEDOR"}""")
			.andExpect(status().isBadRequest());
	}

	@Test
	void listaEBusca() throws Exception {
		Long id = idDe(cadastrar(operador("op@equiptrack.local")));
		cadastrar(solicitante("aluno@equiptrack.local", "MAT-001"));

		comChave(get("/api/usuarios")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
		comChave(get("/api/usuarios/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("op@equiptrack.local"));
		comChave(get("/api/usuarios/999999")).andExpect(status().isNotFound());
	}

	@Test
	void atualizaSoOQueFoiEnviado() throws Exception {
		Long id = idDe(cadastrar(operador("op@equiptrack.local")));

		comChave(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Operadora Renomeada\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nome").value("Operadora Renomeada"))
			.andExpect(jsonPath("$.email").value("op@equiptrack.local"))
			.andExpect(jsonPath("$.perfil").value("OPERADOR"));
	}

	@Test
	void novaSenhaEncerraAsSessoesEPassaAValer() throws Exception {
		Long id = idDe(cadastrar(operador("op@equiptrack.local")));
		String token = entrar("op@equiptrack.local");

		comChave(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"senha\":\"outra-senha-forte\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"op@equiptrack.local\",\"senha\":\"outra-senha-forte\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void desativarImpedeOLogin() throws Exception {
		Long id = idDe(cadastrar(operador("op@equiptrack.local")));

		comChave(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ativo").value(false));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"op@equiptrack.local\",\"senha\":\"senha-forte-1\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void virarSolicitanteExigeOsDados() throws Exception {
		Long id = idDe(cadastrar(operador("op@equiptrack.local")));

		comChave(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"perfil\":\"SOLICITANTE\"}"))
			.andExpect(status().isBadRequest());
		comChave(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"perfil\":\"SOLICITANTE\",\"solicitante\":{\"tipo\":\"PROFESSOR\",\"matricula\":\"MAT-777\"}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.perfil").value("SOLICITANTE"))
			.andExpect(jsonPath("$.solicitante.tipo").value("PROFESSOR"));
	}

	@Test
	void excluiUsuarioSemRegistros() throws Exception {
		Long id = idDe(cadastrar(solicitante("aluno@equiptrack.local", "MAT-001")));
		entrar("aluno@equiptrack.local");

		comChave(delete("/api/usuarios/" + id)).andExpect(status().isNoContent());

		assertThat(usuarioRepository.findById(id)).isEmpty();
		assertThat(solicitanteRepository.findByMatricula("MAT-001")).isEmpty();
		comChave(delete("/api/usuarios/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void naoExcluiQuemTemHistorico() throws Exception {
		Long id = idDe(cadastrar(operador("op@equiptrack.local")));
		String token = entrar("op@equiptrack.local");
		Categoria categoria = categoriaRepository.save(new Categoria("Notebook"));
		// Cadastrar um equipamento grava um evento no histórico em nome da operadora.
		mockMvc.perform(post("/api/equipamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"patrimonio":"NB-0100","categoriaId":%d,"descricao":"Notebook","condicao":"BOA",
						 "prazoMaxHoras":24}""".formatted(categoria.getId())))
			.andExpect(status().isCreated());

		comChave(delete("/api/usuarios/" + id))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.usuario").exists());
		assertThat(usuarioRepository.findById(id)).isPresent();
	}

	private org.springframework.test.web.servlet.ResultActions cadastrar(String corpo) throws Exception {
		return comChave(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private org.springframework.test.web.servlet.ResultActions comChave(MockHttpServletRequestBuilder requisicao)
			throws Exception {
		return mockMvc.perform(requisicao.header("X-Chave-Desenvolvedor", CHAVE));
	}

	private Long idDe(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
		String corpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	private String entrar(String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"senha\":\"senha-forte-1\"}".formatted(email)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private static String usuario(String email, String perfil) {
		return """
				{"nome":"Fulano","email":"%s","senha":"senha-forte-1","perfil":"%s"}""".formatted(email, perfil);
	}

	private static String operador(String email) {
		return usuario(email, "OPERADOR");
	}

	private static String solicitante(String email, String matricula) {
		return """
				{"nome":"Aluno","email":"%s","senha":"senha-forte-1","perfil":"SOLICITANTE",
				 "solicitante":{"tipo":"ALUNO","matricula":"%s"}}""".formatted(email, matricula);
	}

}
