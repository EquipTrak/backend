package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.historico.TipoEvento;
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
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DevolucaoApiTests {

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
	private PasswordEncoder passwordEncoder;

	private Categoria categoria;
	private Equipamento projetor;
	private Usuario operador;
	private Solicitante rafael;
	private Solicitante beatriz;
	private String tokenOperador;
	private String tokenRafael;

	private final Instant agora = Instant.now().truncatedTo(ChronoUnit.MINUTES);

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		categoria = categoriaRepository.save(new Categoria("Diversos"));
		projetor = salvarEquipamento("PJ-0001");
		operador = criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		rafael = criarSolicitante("Rafael Lima", "rafael@equiptrack.local", "MAT-1");
		beatriz = criarSolicitante("Beatriz Alves", "beatriz@equiptrack.local", "MAT-2");
		tokenOperador = entrar("operador@equiptrack.local");
		tokenRafael = entrar("rafael@equiptrack.local");
	}

	// O histórico aponta para o empréstimo, que aponta para a solicitação e o equipamento.
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
	void devolucaoFechaOEmprestimoEDevolveOEquipamentoComANovaCondicao() throws Exception {
		Long emprestimoId = retirar(rafael);

		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"REGULAR\",\"observacao\":\"  Risco na tampa.  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APROVADA"))
			.andExpect(jsonPath("$.emprestimo.id").value(emprestimoId))
			.andExpect(jsonPath("$.emprestimo.status").value("DEVOLVIDO"))
			.andExpect(jsonPath("$.emprestimo.dataDevolucao").isString());

		Emprestimo emprestimo = emprestimoRepository.findById(emprestimoId).orElseThrow();
		assertThat(emprestimo.getStatus()).isEqualTo(StatusEmprestimo.DEVOLVIDO);
		assertThat(emprestimo.getAtivoFlag()).isNull();
		assertThat(emprestimo.getCondicaoDevolucao()).isEqualTo(Condicao.REGULAR);
		assertThat(emprestimo.getObservacaoDevolucao()).isEqualTo("Risco na tampa.");
		assertThat(emprestimo.getOperadorDevolucao().getId()).isEqualTo(operador.getId());
		Equipamento equipamento = equipamentoRepository.findById(projetor.getId()).orElseThrow();
		assertThat(equipamento.getSituacao()).isEqualTo(Situacao.DISPONIVEL);
		assertThat(equipamento.getCondicao()).isEqualTo(Condicao.REGULAR);
	}

	@Test
	void devolucaoComAvariaDeixaOEquipamentoDanificado() throws Exception {
		Long emprestimoId = retirar(rafael);

		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"COM_AVARIA\"}").andExpect(status().isOk());

		Equipamento equipamento = equipamentoRepository.findById(projetor.getId()).orElseThrow();
		assertThat(equipamento.getSituacao()).isEqualTo(Situacao.DANIFICADO);
		assertThat(equipamento.getCondicao()).isEqualTo(Condicao.COM_AVARIA);
	}

	@Test
	void devolucaoGravaUmEventoNoHistoricoComAntesEDepois() throws Exception {
		Long emprestimoId = retirar(rafael);

		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"COM_AVARIA\",\"observacao\":\"Lente trincada.\"}")
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/equipamentos/PJ-0001/historico")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tipo").value("DEVOLUCAO"))
			.andExpect(jsonPath("$[0].usuario.nome").value("Marina Souza"))
			.andExpect(jsonPath("$[0].situacaoAnterior").value("EMPRESTADO"))
			.andExpect(jsonPath("$[0].situacaoNova").value("DANIFICADO"))
			.andExpect(jsonPath("$[0].condicaoAnterior").value("BOA"))
			.andExpect(jsonPath("$[0].condicaoNova").value("COM_AVARIA"))
			.andExpect(jsonPath("$[0].observacao").value("Lente trincada."))
			.andExpect(jsonPath("$[0].emprestimoId").value(emprestimoId))
			.andExpect(jsonPath("$[1].tipo").value("RETIRADA"));
	}

	@Test
	void naoRegistraADevolucaoDuasVezes() throws Exception {
		Long emprestimoId = retirar(rafael);
		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"BOA\"}").andExpect(status().isOk());

		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"COM_AVARIA\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.mensagem").value("A devolução deste empréstimo já foi registrada."));
		assertThat(equipamentoRepository.findById(projetor.getId()).orElseThrow().getSituacao())
			.isEqualTo(Situacao.DISPONIVEL);
		assertThat(historicoRepository.count()).isEqualTo(2);
	}

	@Test
	void depoisDaDevolucaoOEquipamentoPodeSairDeNovo() throws Exception {
		Long primeiro = retirar(rafael);
		SolicitacaoEmprestimo segunda = salvarAprovada(beatriz);
		devolver(primeiro, tokenOperador, "{\"condicao\":\"OTIMA\"}").andExpect(status().isOk());

		mockMvc.perform(post("/api/solicitacoes/" + segunda.getId() + "/retirada")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isCreated());
		assertThat(equipamentoRepository.findById(projetor.getId()).orElseThrow().getSituacao())
			.isEqualTo(Situacao.EMPRESTADO);
	}

	@Test
	void listaDoSolicitanteMostraODevolvido() throws Exception {
		Long emprestimoId = retirar(rafael);
		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"BOA\"}").andExpect(status().isOk());

		mockMvc.perform(get("/api/solicitacoes/minhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].emprestimo.status").value("DEVOLVIDO"));
	}

	@Test
	void condicaoEhObrigatoriaEObservacaoTemLimite() throws Exception {
		Long emprestimoId = retirar(rafael);

		devolver(emprestimoId, tokenOperador, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.condicao").value("Informe a condição do equipamento na devolução."));
		devolver(emprestimoId, tokenOperador, "{\"condicao\":\"PESSIMA\"}").andExpect(status().isBadRequest());
		devolver(emprestimoId, tokenOperador,
				"{\"condicao\":\"BOA\",\"observacao\":\"%s\"}".formatted("x".repeat(501)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.observacao").value("A observação pode ter até 500 caracteres."));
		assertThat(emprestimoRepository.findById(emprestimoId).orElseThrow().getStatus())
			.isEqualTo(StatusEmprestimo.ATIVO);
	}

	@Test
	void emprestimoInexistenteResponde404() throws Exception {
		devolver(999_999L, tokenOperador, "{\"condicao\":\"BOA\"}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.mensagem").value("Empréstimo não encontrado."));
	}

	@Test
	void solicitanteNaoRegistraDevolucaoESemTokenResponde401() throws Exception {
		Long emprestimoId = retirar(rafael);

		devolver(emprestimoId, tokenRafael, "{\"condicao\":\"BOA\"}").andExpect(status().isForbidden());
		mockMvc.perform(post("/api/emprestimos/" + emprestimoId + "/devolucao")
				.contentType(MediaType.APPLICATION_JSON).content("{\"condicao\":\"BOA\"}"))
			.andExpect(status().isUnauthorized());
		assertThat(emprestimoRepository.findById(emprestimoId).orElseThrow().getStatus())
			.isEqualTo(StatusEmprestimo.ATIVO);
	}

	/** Aprova e retira pela API, como no fluxo real; devolve o id do empréstimo. */
	/**
	 * Duas devoluções simultâneas do mesmo empréstimo: só uma pode valer. A outra recebe 409 e não grava
	 * evento nem altera a condição. Sem a trava no empréstimo, as duas passavam pela checagem de status e a
	 * segunda sobrescrevia a primeira (um equipamento com avaria podia voltar como DISPONIVEL).
	 */
	@RepeatedTest(15)
	void devolucoesSimultaneasDoMesmoEmprestimoGravamUmaSoVez() throws Exception {
		Long emprestimoId = retirar(rafael);
		CyclicBarrier largada = new CyclicBarrier(2);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> comAvaria = pool.submit(() -> {
				largada.await();
				return devolver(emprestimoId, tokenOperador, "{\"condicao\":\"COM_AVARIA\"}")
					.andReturn().getResponse().getStatus();
			});
			Future<Integer> semAvaria = pool.submit(() -> {
				largada.await();
				return devolver(emprestimoId, tokenOperador, "{\"condicao\":\"OTIMA\"}")
					.andReturn().getResponse().getStatus();
			});
			List<Integer> respostas = List.of(comAvaria.get(), semAvaria.get());
			assertThat(respostas).containsExactlyInAnyOrder(200, 409);

			long eventosDeDevolucao = historicoRepository.findAll().stream()
				.filter(evento -> evento.getTipo() == TipoEvento.DEVOLUCAO)
				.count();
			assertThat(eventosDeDevolucao).isEqualTo(1);

			// A condição do equipamento é a da devolução que venceu, nunca uma mistura das duas.
			Emprestimo emprestimo = emprestimoRepository.findById(emprestimoId).orElseThrow();
			Equipamento equipamento = equipamentoRepository.findById(projetor.getId()).orElseThrow();
			assertThat(equipamento.getCondicao()).isEqualTo(emprestimo.getCondicaoDevolucao());
			assertThat(equipamento.getSituacao())
				.isEqualTo(emprestimo.getCondicaoDevolucao() == Condicao.COM_AVARIA ? Situacao.DANIFICADO
						: Situacao.DISPONIVEL);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private Long retirar(Solicitante solicitante) throws Exception {
		SolicitacaoEmprestimo solicitacao = salvarAprovada(solicitante);
		String resposta = mockMvc.perform(post("/api/solicitacoes/" + solicitacao.getId() + "/retirada")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.emprestimo.id")).longValue();
	}

	private ResultActions devolver(Long emprestimoId, String token, String corpo) throws Exception {
		return mockMvc.perform(post("/api/emprestimos/" + emprestimoId + "/devolucao")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(corpo));
	}

	private SolicitacaoEmprestimo salvarAprovada(Solicitante solicitante) {
		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(solicitante);
		solicitacao.setEquipamento(projetor);
		solicitacao.setDataInicioPrevista(agora);
		solicitacao.setDataFimPrevista(agora.plus(3, ChronoUnit.HOURS));
		solicitacao.setStatus(StatusSolicitacao.APROVADA);
		return solicitacaoRepository.save(solicitacao);
	}

	private Equipamento salvarEquipamento(String patrimonio) {
		Equipamento novo = new Equipamento();
		novo.setPatrimonio(patrimonio);
		novo.setDescricao("Equipamento " + patrimonio);
		novo.setCategoria(categoria);
		novo.setCondicao(Condicao.BOA);
		novo.setSituacao(Situacao.DISPONIVEL);
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
