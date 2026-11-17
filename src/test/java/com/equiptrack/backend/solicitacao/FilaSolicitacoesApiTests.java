package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FilaSolicitacoesApiTests {

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
	private PasswordEncoder passwordEncoder;

	private Categoria categoria;
	private Equipamento projetor;
	private Equipamento notebook;
	private Usuario operador;
	private Solicitante rafael;
	private Solicitante beatriz;
	private String tokenOperador;
	private String tokenAdministrador;
	private String tokenRafael;

	private final Instant amanha = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		categoria = categoriaRepository.save(new Categoria("Diversos"));
		projetor = salvarEquipamento("PJ-0001", Situacao.DISPONIVEL);
		notebook = salvarEquipamento("NB-0001", Situacao.DISPONIVEL);
		operador = criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		criarUsuario("Administrador", "administrador@equiptrack.local", Perfil.ADMINISTRADOR);
		rafael = criarSolicitante("Rafael Lima", "rafael@equiptrack.local", "MAT-1");
		beatriz = criarSolicitante("Beatriz Alves", "beatriz@equiptrack.local", "MAT-2");
		tokenOperador = entrar("operador@equiptrack.local");
		tokenAdministrador = entrar("administrador@equiptrack.local");
		tokenRafael = entrar("rafael@equiptrack.local");
	}

	// Os outros testes apagam usuários e equipamentos; as solicitações apontam para os dois e saem antes.
	@AfterEach
	void limpar() {
		solicitacaoRepository.deleteAll();
		solicitanteRepository.deleteAll();
		historicoRepository.deleteAll();
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
		sessaoRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	void filaTrazAsSolicitacoesDeTodosDaMaisAntigaParaAMaisRecente() throws Exception {
		SolicitacaoEmprestimo primeira = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);
		SolicitacaoEmprestimo segunda = salvar(beatriz, notebook, amanha, 2, StatusSolicitacao.APROVADA);
		SolicitacaoEmprestimo terceira = salvar(rafael, notebook, amanha, 2, StatusSolicitacao.REJEITADA);

		fila(tokenOperador, "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", contains(primeira.getId().intValue(), segunda.getId().intValue(),
					terceira.getId().intValue())))
			.andExpect(jsonPath("$[0].solicitante.nome").value("Rafael Lima"))
			.andExpect(jsonPath("$[1].solicitante.nome").value("Beatriz Alves"))
			.andExpect(jsonPath("$[1].equipamento.patrimonio").value("NB-0001"));
	}

	@Test
	void filaFiltraPorStatus() throws Exception {
		SolicitacaoEmprestimo pendente = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);
		salvar(beatriz, notebook, amanha, 2, StatusSolicitacao.APROVADA);

		fila(tokenOperador, "?status=PENDENTE")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(pendente.getId()));
		fila(tokenOperador, "?status=RETIRADO").andExpect(status().isBadRequest());
	}

	@Test
	void solicitanteNaoVeAFilaESemTokenResponde401() throws Exception {
		fila(tokenRafael, "").andExpect(status().isForbidden());
		mockMvc.perform(get("/api/solicitacoes")).andExpect(status().isUnauthorized());
	}

	@Test
	void aprovaSolicitacaoPendenteRegistrandoQuemEQuando() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);

		decidir(solicitacao.getId(), "aprovacao", tokenOperador, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APROVADA"))
			.andExpect(jsonPath("$.motivoRejeicao").doesNotExist());

		SolicitacaoEmprestimo salva = solicitacaoRepository.buscarPorId(solicitacao.getId()).orElseThrow();
		assertThat(salva.getStatus()).isEqualTo(StatusSolicitacao.APROVADA);
		assertThat(salva.getDataDecisao()).isNotNull();
		assertThat(salva.getOperadorDecisao().getId()).isEqualTo(operador.getId());
		// Aprovar não é retirar (RN03): o equipamento continua disponível.
		assertThat(equipamentoRepository.findById(projetor.getId()).orElseThrow().getSituacao())
			.isEqualTo(Situacao.DISPONIVEL);
	}

	@Test
	void administradorTambemAprova() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);

		decidir(solicitacao.getId(), "aprovacao", tokenAdministrador, null).andExpect(status().isOk());
	}

	@Test
	void naoAprovaPeriodoSobrepostoAOutraAprovadaDoMesmoEquipamento() throws Exception {
		salvar(beatriz, projetor, amanha, 3, StatusSolicitacao.APROVADA);
		SolicitacaoEmprestimo cruzando = salvar(rafael, projetor, amanha.plus(2, ChronoUnit.HOURS), 2,
				StatusSolicitacao.PENDENTE);

		decidir(cruzando.getId(), "aprovacao", tokenOperador, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.periodo").value(
					"Já existe uma solicitação aprovada para este equipamento em um período que se sobrepõe a este."));
		assertThat(solicitacaoRepository.findById(cruzando.getId()).orElseThrow().getStatus())
			.isEqualTo(StatusSolicitacao.PENDENTE);
	}

	@Test
	void aprovaPeriodoQueComecaQuandoOutroAprovadoTermina() throws Exception {
		salvar(beatriz, projetor, amanha, 3, StatusSolicitacao.APROVADA);
		SolicitacaoEmprestimo emSeguida = salvar(rafael, projetor, amanha.plus(3, ChronoUnit.HOURS), 2,
				StatusSolicitacao.PENDENTE);
		// Mesmo período, mas em outro equipamento, não conta.
		salvar(beatriz, notebook, amanha.plus(3, ChronoUnit.HOURS), 2, StatusSolicitacao.APROVADA);

		decidir(emSeguida.getId(), "aprovacao", tokenOperador, null).andExpect(status().isOk());
	}

	@Test
	void solicitacoesPendentesSobrepostasNaoImpedemAprovar() throws Exception {
		SolicitacaoEmprestimo primeira = salvar(rafael, projetor, amanha, 3, StatusSolicitacao.PENDENTE);
		SolicitacaoEmprestimo segunda = salvar(beatriz, projetor, amanha, 3, StatusSolicitacao.PENDENTE);

		decidir(primeira.getId(), "aprovacao", tokenOperador, null).andExpect(status().isOk());
		// Depois da primeira aprovada, a segunda já sobrepõe.
		decidir(segunda.getId(), "aprovacao", tokenOperador, null).andExpect(status().isConflict());
	}

	@Test
	void naoAprovaEquipamentoDanificadoOuEmManutencao() throws Exception {
		Equipamento danificado = salvarEquipamento("CM-0001", Situacao.DANIFICADO);
		Equipamento manutencao = salvarEquipamento("CM-0002", Situacao.EM_MANUTENCAO);

		for (Equipamento equipamento : new Equipamento[] { danificado, manutencao }) {
			SolicitacaoEmprestimo solicitacao = salvar(rafael, equipamento, amanha, 2, StatusSolicitacao.PENDENTE);
			decidir(solicitacao.getId(), "aprovacao", tokenOperador, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.equipamentoId").exists());
		}
	}

	@Test
	void naoAprovaSolicitanteBloqueado() throws Exception {
		rafael.setStatus(StatusSolicitante.BLOQUEADO);
		solicitanteRepository.save(rafael);
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);

		decidir(solicitacao.getId(), "aprovacao", tokenOperador, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.mensagem").value("O solicitante está bloqueado e não pode ter solicitações aprovadas."));
	}

	@Test
	void rejeitaComMotivo() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);

		decidir(solicitacao.getId(), "rejeicao", tokenOperador, "{\"motivo\":\"  Reservado para aula.  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJEITADA"))
			.andExpect(jsonPath("$.motivoRejeicao").value("Reservado para aula."));

		SolicitacaoEmprestimo salva = solicitacaoRepository.buscarPorId(solicitacao.getId()).orElseThrow();
		assertThat(salva.getOperadorDecisao().getId()).isEqualTo(operador.getId());
		assertThat(salva.getDataDecisao()).isNotNull();
	}

	@Test
	void rejeicaoExigeMotivo() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);

		decidir(solicitacao.getId(), "rejeicao", tokenOperador, "{\"motivo\":\"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.motivo").value("Informe o motivo da rejeição."));
		decidir(solicitacao.getId(), "rejeicao", tokenOperador, "{\"motivo\":\"%s\"}".formatted("x".repeat(256)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.motivo").value("O motivo pode ter até 255 caracteres."));
		assertThat(solicitacaoRepository.findById(solicitacao.getId()).orElseThrow().getStatus())
			.isEqualTo(StatusSolicitacao.PENDENTE);
	}

	@Test
	void soDecideSolicitacaoPendente() throws Exception {
		for (StatusSolicitacao status : new StatusSolicitacao[] { StatusSolicitacao.APROVADA,
				StatusSolicitacao.REJEITADA, StatusSolicitacao.CANCELADA }) {
			SolicitacaoEmprestimo solicitacao = salvar(rafael, notebook, amanha, 2, status);

			decidir(solicitacao.getId(), "aprovacao", tokenOperador, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensagem").value("Esta solicitação não está mais pendente."));
			decidir(solicitacao.getId(), "rejeicao", tokenOperador, "{\"motivo\":\"Motivo\"}")
				.andExpect(status().isConflict());
		}
	}

	@Test
	void solicitacaoInexistenteResponde404() throws Exception {
		decidir(999_999L, "aprovacao", tokenOperador, null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.mensagem").value("Solicitação não encontrada."));
		decidir(999_999L, "rejeicao", tokenOperador, "{\"motivo\":\"Motivo\"}").andExpect(status().isNotFound());
	}

	@Test
	void solicitanteNaoAprovaNemRejeita() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, amanha, 2, StatusSolicitacao.PENDENTE);

		decidir(solicitacao.getId(), "aprovacao", tokenRafael, null).andExpect(status().isForbidden());
		decidir(solicitacao.getId(), "rejeicao", tokenRafael, "{\"motivo\":\"Motivo\"}")
			.andExpect(status().isForbidden());
		assertThat(solicitacaoRepository.findById(solicitacao.getId()).orElseThrow().getStatus())
			.isEqualTo(StatusSolicitacao.PENDENTE);
	}

	private ResultActions fila(String token, String filtro) throws Exception {
		return mockMvc.perform(get("/api/solicitacoes" + filtro).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions decidir(Long id, String acao, String token, String corpo) throws Exception {
		var requisicao = post("/api/solicitacoes/" + id + "/" + acao).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token);
		if (corpo != null) {
			requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
		}
		return mockMvc.perform(requisicao);
	}

	private SolicitacaoEmprestimo salvar(Solicitante solicitante, Equipamento equipamento, Instant inicio, int horas,
			StatusSolicitacao status) throws InterruptedException {
		// Garante datas de criação diferentes, para a ordem da fila ser previsível.
		Thread.sleep(5);
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
