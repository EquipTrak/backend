package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.security.UsuarioAutenticado;
import com.equiptrack.backend.solicitacao.dto.RejeicaoRequest;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoRequest;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

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

	@GetMapping("/minhas")
	@Operation(summary = "Lista as solicitações do usuário logado, da mais recente para a mais antiga (RF18)")
	@ApiResponse(responseCode = "200", description = "Solicitações do usuário; lista vazia se não houver")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão")
	public List<SolicitacaoResponse> listarMinhas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return solicitacaoService.listarMinhas(usuario.id());
	}

	@PostMapping("/{id}/cancelamento")
	@Operation(summary = "Cancela a própria solicitação enquanto estiver PENDENTE (RF23)")
	@ApiResponse(responseCode = "200", description = "Solicitação cancelada")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão")
	@ApiResponse(responseCode = "404", description = "Solicitação inexistente ou de outro solicitante")
	@ApiResponse(responseCode = "409", description = "A solicitação não está mais pendente")
	public SolicitacaoResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return solicitacaoService.cancelar(id, usuario.id());
	}

	@GetMapping
	@Operation(summary = "Fila de solicitações do operador, da mais antiga para a mais recente (RF09)")
	@ApiResponse(responseCode = "200", description = "Solicitações encontradas")
	@ApiResponse(responseCode = "400", description = "Status fora do DER")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	public List<SolicitacaoResponse> listarFila(
			@Parameter(description = "Status exato; sem ele, todos") @RequestParam(required = false) StatusSolicitacao status) {
		return solicitacaoService.listarFila(status);
	}

	@PostMapping("/{id}/aprovacao")
	@Operation(summary = "Aprova uma solicitação pendente (RF09). Aprovar não registra a retirada (RN03)")
	@ApiResponse(responseCode = "200", description = "Solicitação aprovada")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	@ApiResponse(responseCode = "404", description = "Solicitação inexistente")
	@ApiResponse(responseCode = "409", description = "Não está pendente, equipamento indisponível, solicitante bloqueado ou período sobreposto a outra aprovada (RN13)")
	public SolicitacaoResponse aprovar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return solicitacaoService.aprovar(id, usuario.id());
	}

	@PostMapping("/{id}/rejeicao")
	@Operation(summary = "Rejeita uma solicitação pendente, com motivo (RF09)")
	@ApiResponse(responseCode = "200", description = "Solicitação rejeitada")
	@ApiResponse(responseCode = "400", description = "Motivo ausente ou longo demais")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	@ApiResponse(responseCode = "404", description = "Solicitação inexistente")
	@ApiResponse(responseCode = "409", description = "A solicitação não está mais pendente")
	public SolicitacaoResponse rejeitar(@PathVariable Long id, @Valid @RequestBody RejeicaoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return solicitacaoService.rejeitar(id, request.motivo(), usuario.id());
	}

}
