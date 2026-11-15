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
class MinhasSolicitacoesApiTests {

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
	private Solicitante rafael;
	private Solicitante beatriz;
	private String tokenRafael;
	private String tokenBeatriz;
	private String tokenOperador;

	private final Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		categoria = categoriaRepository.save(new Categoria("Diversos"));
		projetor = salvarEquipamento("PJ-0001", "Projetor Epson");
		notebook = salvarEquipamento("NB-0001", "Notebook Dell");
		criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		rafael = criarSolicitante("Rafael Lima", "rafael@equiptrack.local", "MAT-1");
		beatriz = criarSolicitante("Beatriz Alves", "beatriz@equiptrack.local", "MAT-2");
		tokenOperador = entrar("operador@equiptrack.local");
		tokenRafael = entrar("rafael@equiptrack.local");
		tokenBeatriz = entrar("beatriz@equiptrack.local");
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
	void listaSoAsSolicitacoesDoUsuarioDaMaisRecenteParaAMaisAntiga() throws Exception {
		SolicitacaoEmprestimo primeira = salvar(rafael, projetor, StatusSolicitacao.REJEITADA);
		primeira.setMotivoRejeicao("Equipamento reservado para aula.");
		solicitacaoRepository.save(primeira);
		salvar(beatriz, projetor, StatusSolicitacao.PENDENTE);
		SolicitacaoEmprestimo segunda = salvar(rafael, notebook, StatusSolicitacao.PENDENTE);

		mockMvc.perform(get("/api/solicitacoes/minhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].id", contains(segunda.getId().intValue(), primeira.getId().intValue())))
			.andExpect(jsonPath("$[0].status").value("PENDENTE"))
			.andExpect(jsonPath("$[0].equipamento.patrimonio").value("NB-0001"))
			.andExpect(jsonPath("$[0].equipamento.descricao").value("Notebook Dell"))
			.andExpect(jsonPath("$[0].solicitante.nome").value("Rafael Lima"))
			.andExpect(jsonPath("$[0].inicioPrevisto").value(inicio.toString()))
			.andExpect(jsonPath("$[1].status").value("REJEITADA"))
			.andExpect(jsonPath("$[1].motivoRejeicao").value("Equipamento reservado para aula."));
	}

	@Test
	void semSolicitacoesOuSemCadastroDeSolicitanteDevolveListaVazia() throws Exception {
		criarUsuario("Sem Cadastro", "semcadastro@equiptrack.local", Perfil.SOLICITANTE);
		String tokenSemCadastro = entrar("semcadastro@equiptrack.local");

		mockMvc.perform(get("/api/solicitacoes/minhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/solicitacoes/minhas")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSemCadastro))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void operadorNaoTemMinhasSolicitacoesESemTokenResponde401() throws Exception {
		mockMvc.perform(get("/api/solicitacoes/minhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/solicitacoes/minhas")).andExpect(status().isUnauthorized());
	}

	@Test
	void cancelaAPropriaSolicitacaoPendente() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.PENDENTE);

		cancelar(solicitacao.getId(), tokenRafael)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(solicitacao.getId()))
			.andExpect(jsonPath("$.status").value("CANCELADA"));

		SolicitacaoEmprestimo salva = solicitacaoRepository.findById(solicitacao.getId()).orElseThrow();
		assertThat(salva.getStatus()).isEqualTo(StatusSolicitacao.CANCELADA);
		assertThat(salva.getDataDecisao()).isNotNull();
		assertThat(salva.getOperadorDecisao()).isNull();
	}

	@Test
	void depoisDeCancelarPodePedirOMesmoEquipamentoDeNovo() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.PENDENTE);
		cancelar(solicitacao.getId(), tokenRafael).andExpect(status().isOk());

		mockMvc.perform(post("/api/solicitacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"equipamentoId":%d,"inicioPrevisto":"%s","fimPrevisto":"%s"}"""
					.formatted(projetor.getId(), inicio, inicio.plus(2, ChronoUnit.HOURS))))
			.andExpect(status().isCreated());
	}

	@Test
	void soCancelaSolicitacaoPendente() throws Exception {
		for (StatusSolicitacao status : new StatusSolicitacao[] { StatusSolicitacao.APROVADA,
				StatusSolicitacao.REJEITADA, StatusSolicitacao.CANCELADA }) {
			SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, status);

			cancelar(solicitacao.getId(), tokenRafael)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensagem").value("Só é possível cancelar uma solicitação pendente."));
			assertThat(solicitacaoRepository.findById(solicitacao.getId()).orElseThrow().getStatus())
				.isEqualTo(status);
		}
	}

	@Test
	void solicitacaoDeOutraPessoaOuInexistenteResponde404() throws Exception {
		SolicitacaoEmprestimo daBeatriz = salvar(beatriz, projetor, StatusSolicitacao.PENDENTE);

		cancelar(daBeatriz.getId(), tokenRafael)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.mensagem").value("Solicitação não encontrada."));
		cancelar(999_999L, tokenRafael).andExpect(status().isNotFound());
		assertThat(solicitacaoRepository.findById(daBeatriz.getId()).orElseThrow().getStatus())
			.isEqualTo(StatusSolicitacao.PENDENTE);
		// A dona continua podendo cancelar.
		cancelar(daBeatriz.getId(), tokenBeatriz).andExpect(status().isOk());
	}

	@Test
	void operadorNaoCancelaPorEsteEndpointESemTokenResponde401() throws Exception {
		SolicitacaoEmprestimo solicitacao = salvar(rafael, projetor, StatusSolicitacao.PENDENTE);

		cancelar(solicitacao.getId(), tokenOperador).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/solicitacoes/" + solicitacao.getId() + "/cancelamento"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions cancelar(Long id, String token) throws Exception {
		return mockMvc.perform(post("/api/solicitacoes/" + id + "/cancelamento")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private SolicitacaoEmprestimo salvar(Solicitante solicitante, Equipamento equipamento, StatusSolicitacao status)
			throws InterruptedException {
		// Garante datas de criação diferentes, para a ordem da listagem ser previsível.
		Thread.sleep(5);
		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(solicitante);
		solicitacao.setEquipamento(equipamento);
		solicitacao.setDataInicioPrevista(inicio);
		solicitacao.setDataFimPrevista(inicio.plus(3, ChronoUnit.HOURS));
		solicitacao.setStatus(status);
		return solicitacaoRepository.save(solicitacao);
	}

	private Equipamento salvarEquipamento(String patrimonio, String descricao) {
		Equipamento novo = new Equipamento();
		novo.setPatrimonio(patrimonio);
		novo.setDescricao(descricao);
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
