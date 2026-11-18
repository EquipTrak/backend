package com.equiptrack.backend.equipamento;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cria categorias e equipamentos de demonstração em ambiente de desenvolvimento, iguais aos que o front usava
 * como dados fixos. Só cria o que ainda não existe, então pode rodar a cada inicialização.
 */
@Component
@Profile("dev")
public class CatalogoDevInitializer implements CommandLineRunner {

	static final List<String> CATEGORIAS = List.of("Adaptador", "Câmera", "Kit", "Notebook", "Projetor");

	private record EquipamentoDemo(String patrimonio, String descricao, String categoria, Condicao condicao,
			Situacao situacao, int prazoMaxHoras) {
	}

	static final List<EquipamentoDemo> EQUIPAMENTOS = List.of(
			new EquipamentoDemo("NB-0001", "Notebook Dell Latitude", "Notebook", Condicao.BOA, Situacao.DISPONIVEL, 72),
			new EquipamentoDemo("NB-0002", "Notebook Lenovo ThinkPad", "Notebook", Condicao.OTIMA, Situacao.EMPRESTADO, 72),
			new EquipamentoDemo("PJ-0001", "Projetor Epson", "Projetor", Condicao.BOA, Situacao.DISPONIVEL, 24),
			new EquipamentoDemo("CM-0001", "Câmera Canon", "Câmera", Condicao.COM_AVARIA, Situacao.DANIFICADO, 48),
			new EquipamentoDemo("KT-0001", "Kit tripé e iluminação", "Kit", Condicao.OTIMA, Situacao.DISPONIVEL, 48),
			new EquipamentoDemo("AD-0001", "Adaptador HDMI", "Adaptador", Condicao.BOA, Situacao.DISPONIVEL, 24));

	private final CategoriaRepository categoriaRepository;
	private final EquipamentoRepository equipamentoRepository;

	public CatalogoDevInitializer(CategoriaRepository categoriaRepository,
			EquipamentoRepository equipamentoRepository) {
		this.categoriaRepository = categoriaRepository;
		this.equipamentoRepository = equipamentoRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		for (String nome : CATEGORIAS) {
			if (categoriaRepository.findByNomeIgnoreCase(nome).isEmpty()) {
				categoriaRepository.save(new Categoria(nome));
			}
		}
		for (EquipamentoDemo demo : EQUIPAMENTOS) {
			if (!equipamentoRepository.existsByPatrimonioIgnoreCase(demo.patrimonio())) {
				Equipamento equipamento = new Equipamento();
				equipamento.setPatrimonio(demo.patrimonio());
				equipamento.setDescricao(demo.descricao());
				equipamento.setCategoria(categoriaRepository.findByNomeIgnoreCase(demo.categoria()).orElseThrow());
				equipamento.setCondicao(demo.condicao());
				equipamento.setSituacao(demo.situacao());
				equipamento.setPrazoMaxHoras(demo.prazoMaxHoras());
				equipamentoRepository.save(equipamento);
			}
		}
	}

}
