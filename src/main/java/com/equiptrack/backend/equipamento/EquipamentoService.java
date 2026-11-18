package com.equiptrack.backend.equipamento;

import com.equiptrack.backend.equipamento.dto.CategoriaResponse;
import com.equiptrack.backend.equipamento.dto.EquipamentoRequest;
import com.equiptrack.backend.equipamento.dto.EquipamentoResponse;
import com.equiptrack.backend.exception.CampoInvalidoException;
import com.equiptrack.backend.exception.ConflitoException;
import com.equiptrack.backend.historico.HistoricoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class EquipamentoService {

	private final EquipamentoRepository equipamentoRepository;
	private final CategoriaRepository categoriaRepository;
	private final HistoricoService historicoService;

	public EquipamentoService(EquipamentoRepository equipamentoRepository, CategoriaRepository categoriaRepository,
			HistoricoService historicoService) {
		this.equipamentoRepository = equipamentoRepository;
		this.categoriaRepository = categoriaRepository;
		this.historicoService = historicoService;
	}

	@Transactional(readOnly = true)
	public List<CategoriaResponse> listarCategorias() {
		return categoriaRepository.findByAtivaTrueOrderByNomeAsc().stream().map(CategoriaResponse::de).toList();
	}

	@Transactional(readOnly = true)
	public List<EquipamentoResponse> listar(Situacao situacao, Long categoriaId, String busca) {
		String termo = busca == null || busca.isBlank() ? null : "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
		return equipamentoRepository.buscar(situacao, categoriaId, termo).stream()
			.map(EquipamentoResponse::de)
			.toList();
	}

	@Transactional
	public EquipamentoResponse cadastrar(EquipamentoRequest request, Long usuarioId) {
		String patrimonio = request.patrimonio().trim().toUpperCase(Locale.ROOT);
		if (equipamentoRepository.existsByPatrimonioIgnoreCase(patrimonio)) {
			throw new ConflitoException("patrimonio", "Este patrimônio já está cadastrado.");
		}

		Categoria categoria = categoriaRepository.findById(request.categoriaId())
			.filter(Categoria::isAtiva)
			.orElseThrow(() -> new CampoInvalidoException("categoriaId", "Categoria não encontrada ou inativa."));

		Situacao situacao = request.situacao() != null ? request.situacao() : Situacao.DISPONIVEL;
		if (!situacao.permitidaNoCadastro()) {
			throw new CampoInvalidoException("situacao",
					"A situação inicial deve ser DISPONIVEL, DANIFICADO ou EM_MANUTENCAO.");
		}

		Equipamento equipamento = new Equipamento();
		equipamento.setPatrimonio(patrimonio);
		equipamento.setCategoria(categoria);
		equipamento.setDescricao(request.descricao().trim());
		equipamento.setMarca(textoOpcional(request.marca()));
		equipamento.setModelo(textoOpcional(request.modelo()));
		equipamento.setNumeroSerie(textoOpcional(request.numeroSerie()));
		equipamento.setCondicao(request.condicao());
		equipamento.setSituacao(situacao);
		equipamento.setPrazoMaxHoras(request.prazoMaxHoras());
		equipamento = equipamentoRepository.save(equipamento);
		historicoService.registrarCadastro(equipamento, usuarioId);
		return EquipamentoResponse.de(equipamento);
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

}
