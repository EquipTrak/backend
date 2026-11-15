package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.security.UsuarioAutenticado;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api")
@Tag(name = "Empréstimos")
@SecurityRequirement(name = "bearer")
public class EmprestimoController {

	private final EmprestimoService emprestimoService;

	public EmprestimoController(EmprestimoService emprestimoService) {
		this.emprestimoService = emprestimoService;
	}

	@PostMapping("/solicitacoes/{id}/retirada")
	@Operation(summary = "Registra a retirada de uma solicitação aprovada e cria o empréstimo (RF10)")
	@ApiResponse(responseCode = "201", description = "Empréstimo criado; o equipamento passa a EMPRESTADO")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	@ApiResponse(responseCode = "404", description = "Solicitação inexistente")
	@ApiResponse(responseCode = "409", description = "Não aprovada, já retirada, período encerrado, solicitante bloqueado ou equipamento indisponível (RN02, RN04)")
	public ResponseEntity<SolicitacaoResponse> registrarRetirada(@PathVariable Long id,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		SolicitacaoResponse resposta = emprestimoService.registrarRetirada(id, usuario.id());
		return ResponseEntity.created(URI.create("/api/emprestimos/" + resposta.emprestimo().id())).body(resposta);
	}

}
