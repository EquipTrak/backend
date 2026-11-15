package com.equiptrack.backend.historico;

import com.equiptrack.backend.historico.dto.EventoHistoricoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipamentos")
@Tag(name = "Histórico")
@SecurityRequirement(name = "bearer")
public class HistoricoController {

	private final HistoricoService historicoService;

	public HistoricoController(HistoricoService historicoService) {
		this.historicoService = historicoService;
	}

	@GetMapping("/{patrimonio}/historico")
	@Operation(summary = "Histórico do equipamento, do evento mais recente para o mais antigo (RF15). "
			+ "Restrito a operador, encarregado e administrador")
	@ApiResponse(responseCode = "200", description = "Eventos do equipamento")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	@ApiResponse(responseCode = "404", description = "Equipamento não encontrado")
	public List<EventoHistoricoResponse> listar(@PathVariable String patrimonio) {
		return historicoService.listarPorPatrimonio(patrimonio);
	}

}
