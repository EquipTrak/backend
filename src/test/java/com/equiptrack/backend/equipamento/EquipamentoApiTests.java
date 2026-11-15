package com.equiptrack.backend.equipamento;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EquipamentoApiTests {

	private static final String SENHA = "senha-de-teste";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private EquipamentoRepository equipamentoRepository;

	@Autowired
	private HistoricoEquipamentoRepository historicoRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Categoria notebook;
	private Categoria projetor;
	private String tokenOperador;
	private String tokenSolicitante;

	@BeforeEach
	void preparar() throws Exception {
		limpar();

		notebook = categoriaRepository.save(new Categoria("Notebook"));
		projetor = categoriaRepository.save(new Categoria("Projetor"));
		Categoria inativa = new Categoria("Fita VHS");
		inativa.setAtiva(false);
		categoriaRepository.save(inativa);

		salvarEquipamento("NB-0001", "Notebook Dell Latitude", notebook, Situacao.DISPONIVEL);
		salvarEquipamento("NB-0002", "Notebook Lenovo ThinkPad", notebook, Situacao.EMPRESTADO);
		salvarEquipamento("PJ-0001", "Projetor Epson", projetor, Situacao.DISPONIVEL);

		tokenOperador = entrar("operador@equiptrack.local", Perfil.OPERADOR);
		tokenSolicitante = entrar("solicitante@equiptrack.local", Perfil.SOLICITANTE);
	}

	// O cadastro grava histórico, que aponta para usuário e equipamento e precisa sair antes deles.
	@AfterEach
	void limpar() {
		historicoRepository.deleteAll();
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
		sessaoRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	void semTokenAsRotasDeEquipamentoRespondem401() throws Exception {
		mockMvc.perform(get("/api/categorias")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/equipamentos")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/equipamentos").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void listaApenasCategoriasAtivasEmOrdemAlfabetica() throws Exception {
		mockMvc.perform(autenticado(get("/api/categorias"), tokenSolicitante))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].nome", contains("Notebook", "Projetor")))
			.andExpect(jsonPath("$[0].id").value(notebook.getId()));
	}

	@Test
	void listaEquipamentosComCategoriaEmOrdemDePatrimonio() throws Exception {
		mockMvc.perform(autenticado(get("/api/equipamentos"), tokenSolicitante))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].patrimonio", contains("NB-0001", "NB-0002", "PJ-0001")))
			.andExpect(jsonPath("$[0].categoria.nome").value("Notebook"))
			.andExpect(jsonPath("$[0].condicao").value("BOA"))
			.andExpect(jsonPath("$[0].prazoMaxHoras").value(48));
	}

	@Test
	void filtraPorSituacaoCategoriaEBusca() throws Exception {
		mockMvc.perform(autenticado(get("/api/equipamentos").param("situacao", "DISPONIVEL"), tokenOperador))
			.andExpect(jsonPath("$[*].patrimonio", contains("NB-0001", "PJ-0001")));

		mockMvc.perform(autenticado(get("/api/equipamentos").param("categoriaId", projetor.getId().toString()),
				tokenOperador))
			.andExpect(jsonPath("$[*].patrimonio", contains("PJ-0001")));

		mockMvc.perform(autenticado(get("/api/equipamentos").param("busca", "  lenovo "), tokenOperador))
			.andExpect(jsonPath("$[*].patrimonio", contains("NB-0002")));

		mockMvc.perform(autenticado(get("/api/equipamentos").param("busca", "nb-").param("situacao", "EMPRESTADO"),
				tokenOperador))
			.andExpect(jsonPath("$[*].patrimonio", contains("NB-0002")));
	}

	@Test
	void filtroDeSituacaoInvalidoResponde400() throws Exception {
		mockMvc.perform(autenticado(get("/api/equipamentos").param("situacao", "INDISPONIVEL"), tokenOperador))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.situacao").exists());
	}

	@Test
	void operadorCadastraEquipamentoComPatrimonioNormalizado() throws Exception {
		String corpo = """
				{"patrimonio":" nb-0003 ","categoriaId":%d,"descricao":" Notebook HP ","condicao":"REGULAR",
				 "prazoMaxHoras":72}""".formatted(notebook.getId());

		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON).content(corpo))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/equipamentos/")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.patrimonio").value("NB-0003"))
			.andExpect(jsonPath("$.descricao").value("Notebook HP"))
			.andExpect(jsonPath("$.categoria.nome").value("Notebook"))
			.andExpect(jsonPath("$.condicao").value("REGULAR"))
			.andExpect(jsonPath("$.situacao").value("DISPONIVEL"))
			.andExpect(jsonPath("$.prazoMaxHoras").value(72));

		assertThat(equipamentoRepository.existsByPatrimonioIgnoreCase("NB-0003")).isTrue();
	}

	@Test
	void cadastroAceitaSituacaoInicialDanificado() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoValido("CM-0001").replace("}", ",\"situacao\":\"DANIFICADO\"}")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.situacao").value("DANIFICADO"));
	}

	@Test
	void patrimonioRepetidoResponde409MesmoComOutraCaixa() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON).content(corpoValido("nb-0001")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.patrimonio").value("Este patrimônio já está cadastrado."));
	}

	@Test
	void solicitanteNaoPodeCadastrar() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenSolicitante)
				.contentType(MediaType.APPLICATION_JSON).content(corpoValido("NB-0009")))
			.andExpect(status().isForbidden());
		assertThat(equipamentoRepository.existsByPatrimonioIgnoreCase("NB-0009")).isFalse();
	}

	@Test
	void camposObrigatoriosRespondem400ComCadaCampo() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"patrimonio\":\"  \",\"descricao\":\"\",\"prazoMaxHoras\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.patrimonio").exists())
			.andExpect(jsonPath("$.campos.categoriaId").value("Selecione uma categoria."))
			.andExpect(jsonPath("$.campos.descricao").value("Descreva o equipamento."))
			.andExpect(jsonPath("$.campos.condicao").exists())
			.andExpect(jsonPath("$.campos.prazoMaxHoras").exists())
			.andExpect(jsonPath("$.campos", org.hamcrest.Matchers.aMapWithSize(5)));
	}

	@Test
	void patrimonioComCaracteresInvalidosResponde400() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON).content(corpoValido("NB 0003")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.patrimonio").exists());
	}

	@Test
	void categoriaInexistenteOuInativaResponde400() throws Exception {
		Long inativa = categoriaRepository.findByNomeIgnoreCase("Fita VHS").orElseThrow().getId();
		for (long categoriaId : new long[] { 999_999L, inativa }) {
			String corpo = """
					{"patrimonio":"XX-0001","categoriaId":%d,"descricao":"Item","condicao":"BOA","prazoMaxHoras":24}"""
				.formatted(categoriaId);
			mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
					.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.categoriaId").value("Categoria não encontrada ou inativa."));
		}
	}

	@Test
	void naoCadastraComoEmprestado() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoValido("NB-0010").replace("}", ",\"situacao\":\"EMPRESTADO\"}")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.situacao").exists());
	}

	@Test
	void condicaoForaDoDerResponde400() throws Exception {
		mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpoValido("NB-0011").replace("\"BOA\"", "\"PESSIMA\"")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void equipamentoInativoNaoApareceNaLista() throws Exception {
		Equipamento baixado = salvarEquipamento("PJ-0099", "Projetor antigo", projetor, Situacao.DISPONIVEL);
		baixado.setAtivo(false);
		equipamentoRepository.save(baixado);

		mockMvc.perform(autenticado(get("/api/equipamentos"), tokenOperador))
			.andExpect(jsonPath("$", hasSize(3)));
	}

	private String corpoValido(String patrimonio) {
		return """
				{"patrimonio":"%s","categoriaId":%d,"descricao":"Item de teste","condicao":"BOA","prazoMaxHoras":24}"""
			.formatted(patrimonio, notebook.getId());
	}

	private Equipamento salvarEquipamento(String patrimonio, String descricao, Categoria categoria,
			Situacao situacao) {
		Equipamento equipamento = new Equipamento();
		equipamento.setPatrimonio(patrimonio);
		equipamento.setDescricao(descricao);
		equipamento.setCategoria(categoria);
		equipamento.setCondicao(Condicao.BOA);
		equipamento.setSituacao(situacao);
		equipamento.setPrazoMaxHoras(48);
		return equipamentoRepository.save(equipamento);
	}

	private String entrar(String email, Perfil perfil) throws Exception {
		Usuario usuario = new Usuario();
		usuario.setNome(perfil.name());
		usuario.setEmail(email);
		usuario.setSenhaHash(passwordEncoder.encode(SENHA));
		usuario.setPerfil(perfil);
		usuarioRepository.save(usuario);

		String resposta = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder requisicao, String token) {
		return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

}
