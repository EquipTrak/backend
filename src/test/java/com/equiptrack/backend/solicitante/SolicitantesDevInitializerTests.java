package com.equiptrack.backend.solicitante;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.equipamento.CategoriaRepository;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.solicitacao.SolicitacaoRepository;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SolicitantesDevInitializerTests {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	@Autowired
	private SolicitanteRepository solicitanteRepository;

	@Autowired
	private SolicitacaoRepository solicitacaoRepository;

	@Autowired
	private HistoricoEquipamentoRepository historicoRepository;

	@Autowired
	private EquipamentoRepository equipamentoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	private SolicitantesDevInitializer initializer;

	@BeforeEach
	void preparar() {
		limpar();
		initializer = new SolicitantesDevInitializer(usuarioRepository, solicitanteRepository);
	}

	// Os outros testes apagam os usuários; o solicitante aponta para eles e precisa sair antes.
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
	void semUsuariosDeDemonstracaoNadaEhCriado() {
		initializer.run();

		assertThat(solicitanteRepository.count()).isZero();
	}

	@Test
	void criaOCadastroDosSolicitantesDeDemonstracaoSemDuplicar() {
		Usuario rafael = salvarUsuario("Rafael Lima", "rafael.lima@equiptrack.local", Perfil.SOLICITANTE);
		salvarUsuario("Beatriz Alves", "beatriz.alves@equiptrack.local", Perfil.SOLICITANTE);
		salvarUsuario("Marina Souza", "marina.souza@equiptrack.local", Perfil.OPERADOR);

		initializer.run();
		initializer.run();

		assertThat(solicitanteRepository.count()).isEqualTo(2);
		assertThat(solicitanteRepository.findByUsuarioId(rafael.getId())).hasValueSatisfying(solicitante -> {
			assertThat(solicitante.getTipo()).isEqualTo(TipoSolicitante.ALUNO);
			assertThat(solicitante.getStatus()).isEqualTo(StatusSolicitante.ATIVO);
			assertThat(solicitante.getSituacaoMatricula()).isEqualTo(SituacaoMatricula.ATIVA);
		});
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
