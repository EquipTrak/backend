package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.security.UsuarioAutenticado;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoRequest;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/solicitacoes")
@Tag(name = "Solicitações")
@SecurityRequirement(name = "bearer")
public class SolicitacaoController {

	private final SolicitacaoService solicitacaoService;

	public SolicitacaoController(SolicitacaoService solicitacaoService) {
		this.solicitacaoService = solicitacaoService;
	}

	@PostMapping
	@Operation(summary = "Cria uma solicitação de empréstimo para o usuário logado (RF08). Restrito a solicitante")
	@ApiResponse(responseCode = "201", description = "Solicitação criada como PENDENTE")
	@ApiResponse(responseCode = "400", description = "Dados inválidos, período inválido (RN10) ou acima do prazo do equipamento")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão, ou solicitante sem cadastro, bloqueado ou com matrícula inativa")
	@ApiResponse(responseCode = "409", description = "Equipamento indisponível ou solicitação pendente repetida")
	public ResponseEntity<SolicitacaoResponse> criar(@Valid @RequestBody SolicitacaoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		SolicitacaoResponse criada = solicitacaoService.criar(request, usuario.id());
		return ResponseEntity.created(URI.create("/api/solicitacoes/" + criada.id())).body(criada);
	}

}
