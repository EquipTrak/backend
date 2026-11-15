package com.equiptrack.backend.historico;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HistoricoApiTests {

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
	private Usuario operador;
	private String tokenOperador;
	private String tokenSolicitante;

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		notebook = categoriaRepository.save(new Categoria("Notebook"));
		operador = criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		criarUsuario("Rafael Lima", "solicitante@equiptrack.local", Perfil.SOLICITANTE);
		tokenOperador = entrar("operador@equiptrack.local");
		tokenSolicitante = entrar("solicitante@equiptrack.local");
	}

	// Os outros testes apagam usuários e equipamentos; o histórico aponta para os dois e precisa sair antes.
	@AfterEach
	void limpar() {
		historicoRepository.deleteAll();
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
		sessaoRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	void cadastroRegistraOEventoComUsuarioSituacaoECondicao() throws Exception {
		cadastrar("nb-0003", "EM_MANUTENCAO").andExpect(status().isCreated());

		mockMvc.perform(autenticado(get("/api/equipamentos/NB-0003/historico"), tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").isNumber())
			.andExpect(jsonPath("$[0].tipo").value("CADASTRO"))
			.andExpect(jsonPath("$[0].dataHora").isString())
			.andExpect(jsonPath("$[0].usuario.id").value(operador.getId()))
			.andExpect(jsonPath("$[0].usuario.nome").value("Marina Souza"))
			.andExpect(jsonPath("$[0].usuario.email").doesNotExist())
			.andExpect(jsonPath("$[0].situacaoAnterior").doesNotExist())
			.andExpect(jsonPath("$[0].situacaoNova").value("EM_MANUTENCAO"))
			.andExpect(jsonPath("$[0].condicaoNova").value("REGULAR"))
			.andExpect(jsonPath("$[0].observacao").value("Situação inicial definida."));
	}

	@Test
	void cadastroRecusadoNaoDeixaEvento() throws Exception {
		cadastrar("NB-0003", "DISPONIVEL").andExpect(status().isCreated());
		cadastrar("NB-0003", "DISPONIVEL").andExpect(status().isConflict());
		cadastrar("NB-0004", "EMPRESTADO").andExpect(status().isBadRequest());

		assertThat(historicoRepository.count()).isEqualTo(1);
		assertThat(equipamentoRepository.count()).isEqualTo(1);
	}

	@Test
	void listaDoMaisRecenteParaOMaisAntigoESoDoEquipamentoPedido() throws Exception {
		Equipamento nb = salvarEquipamento("NB-0001");
		Equipamento outro = salvarEquipamento("NB-0002");
		salvarEvento(nb, TipoEvento.CADASTRO, "primeiro");
		salvarEvento(outro, TipoEvento.CADASTRO, "de outro equipamento");
		salvarEvento(nb, TipoEvento.MUDANCA_CONDICAO, "segundo");
		salvarEvento(nb, TipoEvento.MANUTENCAO, "terceiro");

		mockMvc.perform(autenticado(get("/api/equipamentos/NB-0001/historico"), tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].observacao", contains("terceiro", "segundo", "primeiro")))
			.andExpect(jsonPath("$[*].tipo", contains("MANUTENCAO", "MUDANCA_CONDICAO", "CADASTRO")));
	}

	@Test
	void patrimonioEhProcuradoSemDiferenciarMaiusculas() throws Exception {
		salvarEvento(salvarEquipamento("NB-0001"), TipoEvento.CADASTRO, "ok");

		mockMvc.perform(autenticado(get("/api/equipamentos/nb-0001/historico"), tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void equipamentoSemEventosDevolveListaVazia() throws Exception {
		salvarEquipamento("NB-0001");

		mockMvc.perform(autenticado(get("/api/equipamentos/NB-0001/historico"), tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void patrimonioInexistenteResponde404NoFormatoPadrao() throws Exception {
		mockMvc.perform(autenticado(get("/api/equipamentos/XX-9999/historico"), tokenOperador))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.mensagem").value("Equipamento não encontrado."))
			.andExpect(jsonPath("$.caminho").value("/api/equipamentos/XX-9999/historico"));
	}

	@Test
	void solicitanteNaoVeOHistorico() throws Exception {
		salvarEvento(salvarEquipamento("NB-0001"), TipoEvento.CADASTRO, "ok");

		mockMvc.perform(autenticado(get("/api/equipamentos/NB-0001/historico"), tokenSolicitante))
			.andExpect(status().isForbidden());
	}

	@Test
	void semTokenResponde401() throws Exception {
		mockMvc.perform(get("/api/equipamentos/NB-0001/historico")).andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions cadastrar(String patrimonio, String situacao)
			throws Exception {
		String corpo = """
				{"patrimonio":"%s","categoriaId":%d,"descricao":"Notebook HP","condicao":"REGULAR",
				 "situacao":"%s","prazoMaxHoras":24}""".formatted(patrimonio, notebook.getId(), situacao);
		return mockMvc.perform(autenticado(post("/api/equipamentos"), tokenOperador)
			.contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private Equipamento salvarEquipamento(String patrimonio) {
		Equipamento equipamento = new Equipamento();
		equipamento.setPatrimonio(patrimonio);
		equipamento.setDescricao("Notebook de teste");
		equipamento.setCategoria(notebook);
		equipamento.setCondicao(Condicao.BOA);
		equipamento.setSituacao(Situacao.DISPONIVEL);
		equipamento.setPrazoMaxHoras(48);
		return equipamentoRepository.save(equipamento);
	}

	private void salvarEvento(Equipamento equipamento, TipoEvento tipo, String observacao) {
		HistoricoEquipamento evento = new HistoricoEquipamento();
		evento.setEquipamento(equipamento);
		evento.setUsuario(operador);
		evento.setTipo(tipo);
		evento.setObservacao(observacao);
		historicoRepository.save(evento);
	}

	private Usuario criarUsuario(String nome, String email, Perfil perfil) {
		Usuario usuario = new Usuario();
		usuario.setNome(nome);
		usuario.setEmail(email);
		usuario.setSenhaHash(passwordEncoder.encode(SENHA));
		usuario.setPerfil(perfil);
		return usuarioRepository.save(usuario);
	}

	private String entrar(String email) throws Exception {
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
