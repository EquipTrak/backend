package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.solicitacao.SolicitacaoEmprestimo;
import com.equiptrack.backend.solicitacao.SolicitacaoRepository;
import com.equiptrack.backend.solicitacao.StatusSolicitacao;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.equiptrack.backend.solicitante.TipoSolicitante;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AtrasoApiTests {

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
	private SolicitanteRepository solicitanteRepository;

	@Autowired
	private SolicitacaoRepository solicitacaoRepository;

	@Autowired
	private EmprestimoRepository emprestimoRepository;

	@Autowired
	private Atrasos atrasos;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Categoria categoria;
	private Equipamento notebook;
	private Equipamento projetor;
	private Usuario operador;
	private Solicitante rafael;
	private String tokenOperador;
	private String tokenRafael;
	private String tokenBeatriz;

	private final Instant agora = Instant.now().truncatedTo(ChronoUnit.MINUTES);

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		categoria = categoriaRepository.save(new Categoria("Diversos"));
		notebook = salvarEquipamento("NB-0001", Situacao.EMPRESTADO);
		projetor = salvarEquipamento("PJ-0001", Situacao.DISPONIVEL);
		operador = criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		rafael = criarSolicitante("Rafael Lima", "rafael@equiptrack.local", "MAT-1");
		criarSolicitante("Beatriz Alves", "beatriz@equiptrack.local", "MAT-2");
		tokenOperador = entrar("operador@equiptrack.local");
		tokenRafael = entrar("rafael@equiptrack.local");
		tokenBeatriz = entrar("beatriz@equiptrack.local");
	}

	@AfterEach
	void limpar() {
		historicoRepository.deleteAll();
		emprestimoRepository.deleteAll();
		solicitacaoRepository.deleteAll();
		solicitanteRepository.deleteAll();
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
		sessaoRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	void emprestimoQuePassouDoDiaPrevistoApareceAtrasadoNasListas() throws Exception {
		emprestimoAberto(rafael, notebook, agora.minus(2, ChronoUnit.DAYS));

		mockMvc.perform(get("/api/solicitacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].emprestimo.status").value("ATRASADO"));
		mockMvc.perform(get("/api/solicitacoes/minhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].emprestimo.status").value("ATRASADO"));
	}

	@Test
	void previstoParaHojeAindaNaoEhAtraso() throws Exception {
		// Primeiro minuto de hoje no fuso: já passou, mas é o próprio dia (RN15).
		emprestimoAberto(rafael, notebook, atrasos.inicioDeHoje().plus(1, ChronoUnit.MINUTES));

		mockMvc.perform(get("/api/solicitacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(jsonPath("$[0].emprestimo.status").value("ATIVO"));
		criar(tokenRafael, projetor).andExpect(status().isCreated());
	}

	@Test
	void solicitanteComAtrasoNaoCriaSolicitacao() throws Exception {
		emprestimoAberto(rafael, notebook, agora.minus(2, ChronoUnit.DAYS));

		criar(tokenRafael, projetor)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.mensagem")
				.value("Você tem um empréstimo em atraso. Devolva o equipamento para fazer novas solicitações."));
		// Quem não tem atraso segue normalmente.
		criar(tokenBeatriz, projetor).andExpect(status().isCreated());
	}

	@Test
	void solicitanteComAtrasoNaoTemAprovacaoNemRetirada() throws Exception {
		SolicitacaoEmprestimo pendente = salvarSolicitacao(rafael, projetor, StatusSolicitacao.PENDENTE, agora, 2);
		Equipamento kit = salvarEquipamento("KT-0001", Situacao.DISPONIVEL);
		SolicitacaoEmprestimo aprovada = salvarSolicitacao(rafael, kit, StatusSolicitacao.APROVADA, agora, 2);
		emprestimoAberto(rafael, notebook, agora.minus(2, ChronoUnit.DAYS));

		mockMvc.perform(post("/api/solicitacoes/" + pendente.getId() + "/aprovacao")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.solicitante").exists());
		mockMvc.perform(post("/api/solicitacoes/" + aprovada.getId() + "/retirada")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.solicitante").exists());
	}

	@Test
	void devolverORegularizaESolicitanteVoltaAPedir() throws Exception {
		Emprestimo atrasado = emprestimoAberto(rafael, notebook, agora.minus(2, ChronoUnit.DAYS));
		criar(tokenRafael, projetor).andExpect(status().isForbidden());

		mockMvc.perform(post("/api/emprestimos/" + atrasado.getId() + "/devolucao")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"condicao\":\"BOA\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emprestimo.status").value("DEVOLVIDO"));

		criar(tokenRafael, projetor).andExpect(status().isCreated());
	}

	private ResultActions criar(String token, Equipamento equipamento) throws Exception {
		Instant inicio = agora.plus(1, ChronoUnit.DAYS);
		return mockMvc.perform(post("/api/solicitacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"equipamentoId":%d,"inicioPrevisto":"%s","fimPrevisto":"%s"}"""
				.formatted(equipamento.getId(), inicio, inicio.plus(2, ChronoUnit.HOURS))));
	}

	private Emprestimo emprestimoAberto(Solicitante solicitante, Equipamento equipamento, Instant prevista) {
		SolicitacaoEmprestimo solicitacao = salvarSolicitacao(solicitante, equipamento, StatusSolicitacao.APROVADA,
				prevista.minus(3, ChronoUnit.HOURS), 3);
		Emprestimo emprestimo = new Emprestimo();
		emprestimo.setSolicitacao(solicitacao);
		emprestimo.setEquipamento(equipamento);
		emprestimo.setOperadorRetirada(operador);
		emprestimo.setDataRetirada(solicitacao.getDataInicioPrevista());
		emprestimo.setDataPrevistaDevolucao(prevista);
		return emprestimoRepository.save(emprestimo);
	}

	private SolicitacaoEmprestimo salvarSolicitacao(Solicitante solicitante, Equipamento equipamento,
			StatusSolicitacao status, Instant inicio, int horas) {
		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(solicitante);
		solicitacao.setEquipamento(equipamento);
		solicitacao.setDataInicioPrevista(inicio);
		solicitacao.setDataFimPrevista(inicio.plus(horas, ChronoUnit.HOURS));
		solicitacao.setStatus(status);
		return solicitacaoRepository.save(solicitacao);
	}

	private Equipamento salvarEquipamento(String patrimonio, Situacao situacao) {
		Equipamento novo = new Equipamento();
		novo.setPatrimonio(patrimonio);
		novo.setDescricao("Equipamento " + patrimonio);
		novo.setCategoria(categoria);
		novo.setCondicao(Condicao.BOA);
		novo.setSituacao(situacao);
		novo.setPrazoMaxHoras(24);
		return equipamentoRepository.save(novo);
	}

	private Solicitante criarSolicitante(String nome, String email, String matricula) {
		Solicitante solicitante = new Solicitante();
		solicitante.setUsuario(criarUsuario(nome, email, Perfil.SOLICITANTE));
		solicitante.setTipo(TipoSolicitante.ALUNO);
		solicitante.setMatricula(matricula);
		return solicitanteRepository.save(solicitante);
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

}
