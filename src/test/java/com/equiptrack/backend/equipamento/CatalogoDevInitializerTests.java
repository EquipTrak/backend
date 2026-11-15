package com.equiptrack.backend.equipamento;

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

	@BeforeEach
	void limpar() {
		equipamentoRepository.deleteAll();
		categoriaRepository.deleteAll();
	}

	@Test
	void criaCatalogoDeDemonstracaoSemDuplicarAoRodarDeNovo() {
		CatalogoDevInitializer initializer = new CatalogoDevInitializer(categoriaRepository, equipamentoRepository);

		initializer.run();
		initializer.run();

		assertThat(categoriaRepository.count()).isEqualTo(CatalogoDevInitializer.CATEGORIAS.size());
		assertThat(equipamentoRepository.count()).isEqualTo(CatalogoDevInitializer.EQUIPAMENTOS.size());
		assertThat(equipamentoRepository.buscar(Situacao.DANIFICADO, null, null))
			.singleElement()
			.satisfies(e -> assertThat(e.getPatrimonio()).isEqualTo("CM-0001"));
	}

}
