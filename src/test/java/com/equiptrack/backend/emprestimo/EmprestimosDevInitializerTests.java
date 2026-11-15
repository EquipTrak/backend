package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.Categoria;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.historico.HistoricoService;
import com.equiptrack.backend.solicitacao.SolicitacaoRepository;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.equiptrack.backend.solicitante.TipoSolicitante;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmprestimosDevInitializerTests {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	@Autowired
	private SolicitanteRepository solicitanteRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private EquipamentoRepository equipamentoRepository;

	@Autowired
	private SolicitacaoRepository solicitacaoRepository;

	@Autowired
	private EmprestimoRepository emprestimoRepository;

	@Autowired
	private HistoricoEquipamentoRepository historicoRepository;

	@Autowired
	private HistoricoService historicoService;

	@Autowired
	private Atrasos atrasos;

	@Autowired
	private Clock clock;

	private EmprestimosDevInitializer initializer;

	@BeforeEach
	void preparar() {
		limpar();
		initializer = new EmprestimosDevInitializer(usuarioRepository, solicitanteRepository, equipamentoRepository,
				solicitacaoRepository, emprestimoRepository, historicoService, clock);
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
	void semOsDadosDeDemonstracaoNadaEhCriado() {
		initializer.run();

		assertThat(emprestimoRepository.count()).isZero();
	}

	@Test
	void criaUmEmprestimoAtrasadoDaBeatrizNoNotebookEmprestadoSemRepetir() {
		Usuario marina = salvarUsuario("Marina Souza", EmprestimosDevInitializer.EMAIL_OPERADOR, Perfil.OPERADOR);
		Usuario beatriz = salvarUsuario("Beatriz Alves", EmprestimosDevInitializer.EMAIL_SOLICITANTE,
				Perfil.SOLICITANTE);
		Solicitante solicitante = new Solicitante();
		solicitante.setUsuario(beatriz);
		solicitante.setTipo(TipoSolicitante.PROFESSOR);
		solicitante.setMatricula("DEMO-0002");
		solicitante = solicitanteRepository.save(solicitante);
		Categoria categoria = categoriaRepository.save(new Categoria("Notebook"));
		Equipamento notebook = new Equipamento();
		notebook.setPatrimonio(EmprestimosDevInitializer.PATRIMONIO);
		notebook.setDescricao("Notebook Lenovo ThinkPad");
		notebook.setCategoria(categoria);
		notebook.setCondicao(Condicao.OTIMA);
		notebook.setSituacao(Situacao.EMPRESTADO);
		notebook.setPrazoMaxHoras(72);
		equipamentoRepository.save(notebook);

		initializer.run();
		initializer.run();

		assertThat(emprestimoRepository.findAll()).singleElement().satisfies(emprestimo -> {
			assertThat(atrasos.statusAtual(emprestimo)).isEqualTo(StatusEmprestimo.ATRASADO);
			assertThat(emprestimo.getOperadorRetirada().getId()).isEqualTo(marina.getId());
		});
		assertThat(atrasos.solicitanteTemAtraso(solicitante.getId())).isTrue();
		assertThat(historicoRepository.count()).isEqualTo(1);
	}

	private Usuario salvarUsuario(String nome, String email, Perfil perfil) {
		Usuario usuario = new Usuario();
		usuario.setNome(nome);
		usuario.setEmail(email);
		usuario.setSenhaHash("hash");
		usuario.setPerfil(perfil);
		return usuarioRepository.save(usuario);
	}

}
