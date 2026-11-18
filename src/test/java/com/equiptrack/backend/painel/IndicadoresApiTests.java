package com.equiptrack.backend.painel;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.emprestimo.Emprestimo;
import com.equiptrack.backend.emprestimo.EmprestimoRepository;
import com.equiptrack.backend.emprestimo.StatusEmprestimo;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IndicadoresApiTests {

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
	private Usuario operador;
	private Solicitante rafael;
	private String tokenOperador;
	private String tokenRafael;

	private final Instant agora = Instant.now().truncatedTo(ChronoUnit.MINUTES);

	@BeforeEach
	void preparar() throws Exception {
		limpar();
		categoria = categoriaRepository.save(new Categoria("Diversos"));
		operador = criarUsuario("Marina Souza", "operador@equiptrack.local", Perfil.OPERADOR);
		Solicitante solicitante = new Solicitante();
		solicitante.setUsuario(criarUsuario("Rafael Lima", "rafael@equiptrack.local", Perfil.SOLICITANTE));
		solicitante.setTipo(TipoSolicitante.ALUNO);
		solicitante.setMatricula("MAT-1");
		rafael = solicitanteRepository.save(solicitante);
		tokenOperador = entrar("operador@equiptrack.local");
		tokenRafael = entrar("rafael@equiptrack.local");
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
	void contaOsEquipamentosAtivosPorSituacaoEOsAtrasadosDentroDosEmprestados() throws Exception {
		salvarEquipamento("A-1", Situacao.DISPONIVEL, true);
		salvarEquipamento("A-2", Situacao.DISPONIVEL, true);
		Equipamento emDia = salvarEquipamento("B-1", Situacao.EMPRESTADO, true);
		Equipamento atrasado = salvarEquipamento("B-2", Situacao.EMPRESTADO, true);
		salvarEquipamento("C-1", Situacao.DANIFICADO, true);
		salvarEquipamento("C-2", Situacao.EM_MANUTENCAO, true);
		// Inativo não entra em nenhum total.
		salvarEquipamento("X-1", Situacao.DISPONIVEL, false);
		emprestimo(emDia, agora.plus(1, ChronoUnit.DAYS), StatusEmprestimo.ATIVO);
		emprestimo(atrasado, agora.minus(2, ChronoUnit.DAYS), StatusEmprestimo.ATIVO);

		mockMvc.perform(get("/api/painel/indicadores").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.disponiveis").value(2))
			.andExpect(jsonPath("$.emprestados").value(2))
			.andExpect(jsonPath("$.atrasados").value(1))
			.andExpect(jsonPath("$.indisponiveis").value(2))
			.andExpect(jsonPath("$.total").value(6));
	}

	@Test
	void emprestimoDevolvidoNaoContaComoAtrasado() throws Exception {
		Equipamento devolvido = salvarEquipamento("B-1", Situacao.DISPONIVEL, true);
		emprestimo(devolvido, agora.minus(2, ChronoUnit.DAYS), StatusEmprestimo.DEVOLVIDO);

		mockMvc.perform(get("/api/painel/indicadores").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(jsonPath("$.atrasados").value(0))
			.andExpect(jsonPath("$.disponiveis").value(1));
	}

	@Test
	void semEquipamentosTudoZero() throws Exception {
		mockMvc.perform(get("/api/painel/indicadores").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.disponiveis").value(0))
			.andExpect(jsonPath("$.emprestados").value(0))
			.andExpect(jsonPath("$.atrasados").value(0))
			.andExpect(jsonPath("$.indisponiveis").value(0))
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void solicitanteNaoVeOPainelESemTokenResponde401() throws Exception {
		mockMvc.perform(get("/api/painel/indicadores").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/painel/indicadores")).andExpect(status().isUnauthorized());
	}

	private void emprestimo(Equipamento equipamento, Instant prevista, StatusEmprestimo status) {
		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(rafael);
		solicitacao.setEquipamento(equipamento);
		solicitacao.setDataInicioPrevista(prevista.minus(3, ChronoUnit.HOURS));
		solicitacao.setDataFimPrevista(prevista);
		solicitacao.setStatus(StatusSolicitacao.APROVADA);
		solicitacao = solicitacaoRepository.save(solicitacao);

		Emprestimo emprestimo = new Emprestimo();
		emprestimo.setSolicitacao(solicitacao);
		emprestimo.setEquipamento(equipamento);
		emprestimo.setOperadorRetirada(operador);
		emprestimo.setDataRetirada(solicitacao.getDataInicioPrevista());
		emprestimo.setDataPrevistaDevolucao(prevista);
		emprestimo.setStatus(status);
		emprestimo.setAtivoFlag(status == StatusEmprestimo.ATIVO ? Boolean.TRUE : null);
		emprestimoRepository.save(emprestimo);
	}

	private Equipamento salvarEquipamento(String patrimonio, Situacao situacao, boolean ativo) {
		Equipamento novo = new Equipamento();
		novo.setPatrimonio(patrimonio);
		novo.setDescricao("Equipamento " + patrimonio);
		novo.setCategoria(categoria);
		novo.setCondicao(Condicao.BOA);
		novo.setSituacao(situacao);
		novo.setPrazoMaxHoras(24);
		novo.setAtivo(ativo);
		return equipamentoRepository.save(novo);
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
