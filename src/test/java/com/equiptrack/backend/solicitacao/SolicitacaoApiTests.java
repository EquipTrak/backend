package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.solicitante.SituacaoMatricula;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SolicitacaoApiTests {

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

	private Categoria projetor;
	private Equipamento equipamento;
	private Solicitante rafael;
	private String tokenRafael;
	private String tokenOperador;

	private final Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		projetor = categoriaRepository.save(new Categoria("Projetor"));
		equipamento = salvarEquipamento("PJ-0001", Situacao.DISPONIVEL, 24);
		criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		rafael = criarSolicitante("Rafael Lima", "rafael@equiptrack.local", "MAT-1");
		tokenOperador = entrar("operador@equiptrack.local");
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
	void criaSolicitacaoPendenteParaOUsuarioLogado() throws Exception {
		Instant fim = inicio.plus(3, ChronoUnit.HOURS);

		solicitar(tokenRafael, equipamento.getId(), inicio, fim)
			.andExpect(status().isCreated())
			.andExpect(header().exists(HttpHeaders.LOCATION))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.status").value("PENDENTE"))
			.andExpect(jsonPath("$.solicitante.id").value(rafael.getId()))
			.andExpect(jsonPath("$.solicitante.nome").value("Rafael Lima"))
			.andExpect(jsonPath("$.equipamento.id").value(equipamento.getId()))
			.andExpect(jsonPath("$.equipamento.patrimonio").value("PJ-0001"))
			.andExpect(jsonPath("$.equipamento.descricao").value("Equipamento de teste"))
			.andExpect(jsonPath("$.inicioPrevisto").value(inicio.toString()))
			.andExpect(jsonPath("$.fimPrevisto").value(fim.toString()))
			.andExpect(jsonPath("$.dataSolicitacao").isString())
			.andExpect(jsonPath("$.motivoRejeicao").doesNotExist());

		assertThat(solicitacaoRepository.count()).isEqualTo(1);
		// Pedir não muda a situação do equipamento (RN14).
		assertThat(equipamentoRepository.findById(equipamento.getId()).orElseThrow().getSituacao())
			.isEqualTo(Situacao.DISPONIVEL);
	}

	@Test
	void periodoIgualAoPrazoMaximoEhAceito() throws Exception {
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.plus(24, ChronoUnit.HOURS))
			.andExpect(status().isCreated());
	}

	@Test
	void periodoAcimaDoPrazoMaximoDoEquipamentoEhRecusado() throws Exception {
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.plus(25, ChronoUnit.HOURS))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.fimPrevisto").value("O prazo máximo deste equipamento é de 24 horas."));
		assertThat(solicitacaoRepository.count()).isZero();
	}

	@Test
	void inicioNoPassadoEhRecusado() throws Exception {
		Instant ontem = Instant.now().minus(1, ChronoUnit.DAYS);

		solicitar(tokenRafael, equipamento.getId(), ontem, ontem.plus(2, ChronoUnit.HOURS))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.inicioPrevisto").value("O início não pode estar no passado."));
	}

	@Test
	void inicioAgoraEhAceitoMesmoChegandoComAlgunsSegundosDeAtraso() throws Exception {
		Instant agora = Instant.now().minus(1, ChronoUnit.MINUTES);

		solicitar(tokenRafael, equipamento.getId(), agora, agora.plus(2, ChronoUnit.HOURS))
			.andExpect(status().isCreated());
	}

	@Test
	void fimIgualOuAnteriorAoInicioEhRecusado() throws Exception {
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.fimPrevisto").value("O fim deve ser posterior ao início."));
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.minus(1, ChronoUnit.HOURS))
			.andExpect(status().isBadRequest());
	}

	@Test
	void camposObrigatoriosSaoValidados() throws Exception {
		enviar(tokenRafael, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.equipamentoId").value("Informe o equipamento."))
			.andExpect(jsonPath("$.campos.inicioPrevisto").value("Informe a data e hora de início."))
			.andExpect(jsonPath("$.campos.fimPrevisto").value("Informe a data e hora de fim."));
	}

	@Test
	void dataEmFormatoInvalidoResponde400() throws Exception {
		enviar(tokenRafael, """
				{"equipamentoId":%d,"inicioPrevisto":"05/10/2026 09:00","fimPrevisto":"05/10/2026 12:00"}"""
			.formatted(equipamento.getId())).andExpect(status().isBadRequest());
	}

	@Test
	void equipamentoInexistenteOuInativoEhRecusado() throws Exception {
		Equipamento inativo = salvarEquipamento("PJ-0002", Situacao.DISPONIVEL, 24);
		inativo.setAtivo(false);
		equipamentoRepository.save(inativo);

		solicitar(tokenRafael, 999_999L, inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.equipamentoId").value("Equipamento não encontrado."));
		solicitar(tokenRafael, inativo.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isBadRequest());
	}

	@Test
	void equipamentoQueNaoEstaDisponivelEhRecusado() throws Exception {
		Equipamento emprestado = salvarEquipamento("PJ-0002", Situacao.EMPRESTADO, 24);
		Equipamento danificado = salvarEquipamento("PJ-0003", Situacao.DANIFICADO, 24);

		solicitar(tokenRafael, emprestado.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.campos.equipamentoId").value("Este equipamento não está disponível."));
		solicitar(tokenRafael, danificado.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isConflict());
	}

	@Test
	void segundaSolicitacaoPendenteParaOMesmoEquipamentoEhRecusada() throws Exception {
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isCreated());

		solicitar(tokenRafael, equipamento.getId(), inicio.plus(2, ChronoUnit.DAYS),
				inicio.plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.mensagem").value("Você já tem uma solicitação pendente para este equipamento."));
		assertThat(solicitacaoRepository.count()).isEqualTo(1);
	}

	@Test
	void outroSolicitantePodePedirOMesmoEquipamento() throws Exception {
		criarSolicitante("Beatriz Alves", "beatriz@equiptrack.local", "MAT-2");
		String tokenBeatriz = entrar("beatriz@equiptrack.local");

		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isCreated());
		solicitar(tokenBeatriz, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.solicitante.nome").value("Beatriz Alves"));
	}

	@Test
	void solicitanteBloqueadoOuComMatriculaInativaNaoSolicita() throws Exception {
		rafael.setStatus(StatusSolicitante.BLOQUEADO);
		solicitanteRepository.save(rafael);
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.mensagem").value("Seu cadastro está bloqueado para novas solicitações."));

		rafael.setStatus(StatusSolicitante.ATIVO);
		rafael.setSituacaoMatricula(SituacaoMatricula.INATIVA);
		solicitanteRepository.save(rafael);
		solicitar(tokenRafael, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.mensagem").value("Sua matrícula está inativa."));
	}

	@Test
	void usuarioSolicitanteSemCadastroDeSolicitanteResponde403() throws Exception {
		criarUsuario("Sem Cadastro", "semcadastro@equiptrack.local", Perfil.SOLICITANTE);
		String token = entrar("semcadastro@equiptrack.local");

		solicitar(token, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.mensagem")
				.value("Seu usuário não tem cadastro de solicitante. Procure o operador."));
	}

	@Test
	void operadorNaoCriaSolicitacao() throws Exception {
		solicitar(tokenOperador, equipamento.getId(), inicio, inicio.plus(1, ChronoUnit.HOURS))
			.andExpect(status().isForbidden());
	}

	@Test
	void semTokenResponde401() throws Exception {
		mockMvc.perform(post("/api/solicitacoes").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions solicitar(String token, Long equipamentoId, Instant inicioPrevisto, Instant fimPrevisto)
			throws Exception {
		return enviar(token, """
				{"equipamentoId":%d,"inicioPrevisto":"%s","fimPrevisto":"%s"}"""
			.formatted(equipamentoId, inicioPrevisto, fimPrevisto));
	}

	private ResultActions enviar(String token, String corpo) throws Exception {
		return mockMvc.perform(post("/api/solicitacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(corpo));
	}

	private Equipamento salvarEquipamento(String patrimonio, Situacao situacao, int prazoMaxHoras) {
		Equipamento novo = new Equipamento();
		novo.setPatrimonio(patrimonio);
		novo.setDescricao("Equipamento de teste");
		novo.setCategoria(projetor);
		novo.setCondicao(Condicao.BOA);
		novo.setSituacao(situacao);
		novo.setPrazoMaxHoras(prazoMaxHoras);
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
