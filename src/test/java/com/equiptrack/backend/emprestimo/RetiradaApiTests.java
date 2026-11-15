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
import com.equiptrack.backend.solicitante.StatusSolicitante;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RetiradaApiTests {

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
		projetor = salvarEquipamento("PJ-0001", Situacao.DISPONIVEL);
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
	void retiradaCriaOEmprestimoEPassaOEquipamentoAEmprestado() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);

		retirar(solicitacao.getId(), tokenOperador)
			.andExpect(status().isCreated())
			.andExpect(header().exists(HttpHeaders.LOCATION))
			.andExpect(jsonPath("$.id").value(solicitacao.getId()))
			.andExpect(jsonPath("$.status").value("APROVADA"))
			.andExpect(jsonPath("$.emprestimo.id").isNumber())
			.andExpect(jsonPath("$.emprestimo.status").value("ATIVO"))
			.andExpect(jsonPath("$.emprestimo.dataRetirada").isString())
			.andExpect(jsonPath("$.emprestimo.dataPrevistaDevolucao")
				.value(solicitacao.getDataFimPrevista().toString()))
			.andExpect(jsonPath("$.emprestimo.dataDevolucao").doesNotExist());

		Emprestimo emprestimo = emprestimoRepository.findBySolicitacaoId(solicitacao.getId()).orElseThrow();
		assertThat(emprestimo.getStatus()).isEqualTo(StatusEmprestimo.ATIVO);
		assertThat(emprestimo.getAtivoFlag()).isTrue();
		assertThat(emprestimo.getOperadorRetirada().getId()).isEqualTo(operador.getId());
		assertThat(equipamentoRepository.findById(projetor.getId()).orElseThrow().getSituacao())
			.isEqualTo(Situacao.EMPRESTADO);
	}

	@Test
	void retiradaGravaOEventoNoHistoricoComOEmprestimo() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);
		retirar(solicitacao.getId(), tokenOperador).andExpect(status().isCreated());
		Long emprestimoId = emprestimoRepository.findBySolicitacaoId(solicitacao.getId()).orElseThrow().getId();

		mockMvc.perform(get("/api/equipamentos/PJ-0001/historico")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tipo").value("RETIRADA"))
			.andExpect(jsonPath("$[0].usuario.nome").value("Marina Souza"))
			.andExpect(jsonPath("$[0].situacaoAnterior").value("DISPONIVEL"))
			.andExpect(jsonPath("$[0].situacaoNova").value("EMPRESTADO"))
			.andExpect(jsonPath("$[0].observacao").value("Retirado por Rafael Lima."))
			.andExpect(jsonPath("$[0].emprestimoId").value(emprestimoId));
	}

	@Test
	void listasMostramOEmprestimoDepoisDaRetirada() throws Exception {
		SolicitacaoEmprestimo retirada = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);
		salvar(rafael, salvarEquipamento("NB-0001", Situacao.DISPONIVEL), StatusSolicitacao.PENDENTE, 3);
		retirar(retirada.getId(), tokenOperador).andExpect(status().isCreated());

		mockMvc.perform(get("/api/solicitacoes/minhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].emprestimo").doesNotExist())
			.andExpect(jsonPath("$[1].emprestimo.status").value("ATIVO"));
		mockMvc.perform(get("/api/solicitacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].emprestimo.status").value("ATIVO"))
			.andExpect(jsonPath("$[1].emprestimo").doesNotExist());
	}

	@Test
	void soRetiraSolicitacaoAprovada() throws Exception {
		for (StatusSolicitacao status : new StatusSolicitacao[] { StatusSolicitacao.PENDENTE,
				StatusSolicitacao.REJEITADA, StatusSolicitacao.CANCELADA }) {
			SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, status, 3);

			retirar(solicitacao.getId(), tokenOperador)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensagem").value("Só é possível registrar a retirada de uma solicitação aprovada."));
		}
		assertThat(emprestimoRepository.count()).isZero();
	}

	@Test
	void naoRegistraARetiradaDuasVezes() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);
		retirar(solicitacao.getId(), tokenOperador).andExpect(status().isCreated());

		retirar(solicitacao.getId(), tokenOperador)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.mensagem").value("A retirada desta solicitação já foi registrada."));
		assertThat(emprestimoRepository.count()).isEqualTo(1);
	}

	@Test
	void equipamentoEmprestadoNaoSaiDeNovo() throws Exception {
		SolicitacaoEmprestimo primeira = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);
		SolicitacaoEmprestimo segunda = salvar(beatriz, projetor, StatusSolicitacao.APROVADA, 3);
		retirar(primeira.getId(), tokenOperador).andExpect(status().isCreated());

		retirar(segunda.getId(), tokenOperador)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.equipamentoId").exists());
		assertThat(emprestimoRepository.count()).isEqualTo(1);
		assertThat(historicoRepository.count()).isEqualTo(1);
	}

	@Test
	void equipamentoDanificadoOuEmManutencaoNaoSai() throws Exception {
		for (Situacao situacao : new Situacao[] { Situacao.DANIFICADO, Situacao.EM_MANUTENCAO }) {
			Equipamento equipamento = salvarEquipamento("CM-" + situacao.ordinal(), situacao);
			SolicitacaoEmprestimo solicitacao = salvar(rafael, equipamento, StatusSolicitacao.APROVADA, 3);

			retirar(solicitacao.getId(), tokenOperador).andExpect(status().isConflict());
			assertThat(equipamentoRepository.findById(equipamento.getId()).orElseThrow().getSituacao())
				.isEqualTo(situacao);
		}
	}

	@Test
	void periodoEncerradoNaoPermiteRetirada() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);
		solicitacao.setDataInicioPrevista(agora.minus(5, ChronoUnit.HOURS));
		solicitacao.setDataFimPrevista(agora.minus(1, ChronoUnit.HOURS));
		solicitacaoRepository.save(solicitacao);

		retirar(solicitacao.getId(), tokenOperador)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.periodo").exists());
	}

	@Test
	void solicitanteBloqueadoNaoRetira() throws Exception {
		rafael.setStatus(StatusSolicitante.BLOQUEADO);
		solicitanteRepository.save(rafael);
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);

		retirar(solicitacao.getId(), tokenOperador)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.solicitante").exists());
	}

	@Test
	void solicitacaoInexistenteResponde404() throws Exception {
		retirar(999_999L, tokenOperador)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.mensagem").value("Solicitação não encontrada."));
	}

	@Test
	void solicitanteNaoRegistraRetiradaESemTokenResponde401() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);

		retirar(solicitacao.getId(), tokenRafael).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/solicitacoes/" + solicitacao.getId() + "/retirada"))
			.andExpect(status().isUnauthorized());
		assertThat(emprestimoRepository.count()).isZero();
	}

	@Test
	void bancoImpedeDoisEmprestimosEmAbertoDoMesmoEquipamento() throws Exception {
		SolicitacaoEmprestimo primeira = salvar(rafael, projetor, StatusSolicitacao.APROVADA, 3);
		SolicitacaoEmprestimo segunda = salvar(beatriz, projetor, StatusSolicitacao.APROVADA, 3);
		emprestimoRepository.saveAndFlush(emprestimo(primeira, Boolean.TRUE));

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> emprestimoRepository.saveAndFlush(emprestimo(segunda, Boolean.TRUE)))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		// Depois da devolução (flag nula), outro empréstimo do mesmo equipamento é aceito.
		emprestimoRepository.saveAndFlush(emprestimo(segunda, null));
	}

	private Emprestimo emprestimo(SolicitacaoEmprestimo solicitacao, Boolean ativoFlag) {
		Emprestimo emprestimo = new Emprestimo();
		emprestimo.setSolicitacao(solicitacao);
		emprestimo.setEquipamento(projetor);
		emprestimo.setOperadorRetirada(operador);
		emprestimo.setDataRetirada(agora);
		emprestimo.setDataPrevistaDevolucao(solicitacao.getDataFimPrevista());
		emprestimo.setAtivoFlag(ativoFlag);
		return emprestimo;
	}

	private ResultActions retirar(Long id, String token) throws Exception {
		return mockMvc.perform(post("/api/solicitacoes/" + id + "/retirada")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private SolicitacaoEmprestimo salvar(Solicitante solicitante, Equipamento equipamento, StatusSolicitacao status,
			int horas) throws InterruptedException {
		// Garante datas de criação diferentes, para a ordem das listas ser previsível.
		Thread.sleep(5);
		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(solicitante);
		solicitacao.setEquipamento(equipamento);
		solicitacao.setDataInicioPrevista(agora);
		solicitacao.setDataFimPrevista(agora.plus(horas, ChronoUnit.HOURS));
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
