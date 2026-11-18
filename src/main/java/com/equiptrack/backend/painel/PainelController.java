package com.equiptrack.backend.painel;

import com.equiptrack.backend.emprestimo.Atrasos;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/painel")
@Tag(name = "Painel")
@SecurityRequirement(name = "bearer")
public class PainelController {

	/**
	 * Totais do dashboard (RF20). {@code atrasados} é um subconjunto de {@code emprestados}, então os quatro
	 * números não somam {@code total}.
	 */
	public record IndicadoresResponse(long disponiveis, long emprestados, long atrasados, long indisponiveis,
			long total) {
	}

	private final EquipamentoRepository equipamentoRepository;
	private final Atrasos atrasos;

	public PainelController(EquipamentoRepository equipamentoRepository, Atrasos atrasos) {
		this.equipamentoRepository = equipamentoRepository;
		this.atrasos = atrasos;
	}

	@GetMapping("/indicadores")
	@Transactional(readOnly = true)
	@Operation(summary = "Totais de equipamentos disponíveis, emprestados, atrasados e indisponíveis (RF20)")
	@ApiResponse(responseCode = "200", description = "Totais, contando só equipamentos ativos")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	public IndicadoresResponse indicadores() {
		long disponiveis = equipamentoRepository.countByAtivoTrueAndSituacao(Situacao.DISPONIVEL);
		long emprestados = equipamentoRepository.countByAtivoTrueAndSituacao(Situacao.EMPRESTADO);
		long indisponiveis = equipamentoRepository.countByAtivoTrueAndSituacao(Situacao.DANIFICADO)
				+ equipamentoRepository.countByAtivoTrueAndSituacao(Situacao.EM_MANUTENCAO);
		return new IndicadoresResponse(disponiveis, emprestados, atrasos.contarAtrasados(), indisponiveis,
				equipamentoRepository.countByAtivoTrue());
	}

}
