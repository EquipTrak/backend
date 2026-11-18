package com.equiptrack.backend.equipamento;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.historico.HistoricoService;
import com.equiptrack.backend.historico.TipoEvento;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogoDevInitializerTests {

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private EquipamentoRepository equipamentoRepository;

	@Autowired
	private HistoricoEquipamentoRepository historicoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	@Autowired
	private HistoricoService historicoService;

	private CatalogoDevInitializer initializer;

	@BeforeEach
	void limpar() {
		historicoRepository.deleteAll();
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
		sessaoRepository.deleteAll();
		usuarioRepository.deleteAll();
		initializer = new CatalogoDevInitializer(categoriaRepository, equipamentoRepository, usuarioRepository,
				historicoService);
	}

	@Test
	void criaCatalogoDeDemonstracaoSemDuplicarAoRodarDeNovo() {
		initializer.run();
		initializer.run();

		assertThat(categoriaRepository.count()).isEqualTo(CatalogoDevInitializer.CATEGORIAS.size());
		assertThat(equipamentoRepository.count()).isEqualTo(CatalogoDevInitializer.EQUIPAMENTOS.size());
		assertThat(equipamentoRepository.buscar(Situacao.DANIFICADO, null, null))
			.singleElement()
			.satisfies(e -> assertThat(e.getPatrimonio()).isEqualTo("CM-0001"));
		assertThat(historicoRepository.count()).as("sem a operadora de demonstração não há histórico").isZero();
	}

	@Test
	void registraOCadastroNoHistoricoQuandoAOperadoraDeDemonstracaoExiste() {
		Usuario operadora = new Usuario();
		operadora.setNome("Marina Souza");
		operadora.setEmail(CatalogoDevInitializer.EMAIL_OPERADOR);
		operadora.setSenhaHash("hash");
		operadora.setPerfil(Perfil.OPERADOR);
		usuarioRepository.save(operadora);

		initializer.run();
		initializer.run();

		assertThat(historicoRepository.findAll())
			.hasSize(CatalogoDevInitializer.EQUIPAMENTOS.size())
			.allSatisfy(evento -> assertThat(evento.getTipo()).isEqualTo(TipoEvento.CADASTRO));
	}

}
