package com.equiptrack.backend.equipamento;

import com.equiptrack.backend.historico.HistoricoService;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cria categorias e equipamentos de demonstração em ambiente de desenvolvimento, iguais aos que o front usava
 * como dados fixos. Só cria o que ainda não existe, então pode rodar a cada inicialização.
 */
@Component
@Profile("dev")
@Order(2) // Depois do UsuariosDevInitializer, que cria quem assina o histórico.
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

	/** Operadora de demonstração que assina o evento de cadastro no histórico. */
	static final String EMAIL_OPERADOR = "marina.souza@equiptrack.local";

	private final CategoriaRepository categoriaRepository;
	private final EquipamentoRepository equipamentoRepository;
	private final UsuarioRepository usuarioRepository;
	private final HistoricoService historicoService;

	public CatalogoDevInitializer(CategoriaRepository categoriaRepository,
			EquipamentoRepository equipamentoRepository, UsuarioRepository usuarioRepository,
			HistoricoService historicoService) {
		this.categoriaRepository = categoriaRepository;
		this.equipamentoRepository = equipamentoRepository;
		this.usuarioRepository = usuarioRepository;
		this.historicoService = historicoService;
	}

	@Override
	@Transactional
	public void run(String... args) {
		// Sem EQUIPTRACK_DEV_SENHA não há usuários de demonstração; o catálogo é criado sem histórico.
		Long operadorId = usuarioRepository.findByEmailIgnoreCase(EMAIL_OPERADOR).map(Usuario::getId).orElse(null);
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
				equipamento = equipamentoRepository.save(equipamento);
				if (operadorId != null) {
					historicoService.registrarCadastro(equipamento, operadorId);
				}
			}
		}
	}

}
