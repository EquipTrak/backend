package com.equiptrack.backend.equipamento;

import com.equiptrack.backend.equipamento.dto.CategoriaResponse;
import com.equiptrack.backend.equipamento.dto.EquipamentoRequest;
import com.equiptrack.backend.equipamento.dto.EquipamentoResponse;
import com.equiptrack.backend.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Equipamentos")
@SecurityRequirement(name = "bearer")
public class EquipamentoController {

	private final EquipamentoService equipamentoService;

	public EquipamentoController(EquipamentoService equipamentoService) {
		this.equipamentoService = equipamentoService;
	}

	@GetMapping("/categorias")
	@Operation(summary = "Lista as categorias ativas, em ordem alfabética")
	@ApiResponse(responseCode = "200", description = "Categorias ativas")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	public List<CategoriaResponse> listarCategorias() {
		return equipamentoService.listarCategorias();
	}

	@GetMapping("/equipamentos")
	@Operation(summary = "Lista os equipamentos ativos, com filtros opcionais (RF05, RF19)")
	@ApiResponse(responseCode = "200", description = "Equipamentos encontrados, em ordem de patrimônio")
	@ApiResponse(responseCode = "400", description = "Filtro com valor inválido")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	public List<EquipamentoResponse> listar(
			@Parameter(description = "Situação exata") @RequestParam(required = false) Situacao situacao,
			@Parameter(description = "ID da categoria") @RequestParam(required = false) Long categoriaId,
			@Parameter(description = "Trecho do patrimônio ou da descrição") @RequestParam(required = false) String busca) {
		return equipamentoService.listar(situacao, categoriaId, busca);
	}

	@PostMapping("/equipamentos")
	@Operation(summary = "Cadastra um equipamento (RF04). Restrito a operador e administrador")
	@ApiResponse(responseCode = "201", description = "Equipamento cadastrado")
	@ApiResponse(responseCode = "400", description = "Dados inválidos, categoria inexistente ou situação não permitida")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	@ApiResponse(responseCode = "403", description = "Perfil sem permissão (solicitante)")
	@ApiResponse(responseCode = "409", description = "Patrimônio já cadastrado")
	public ResponseEntity<EquipamentoResponse> cadastrar(@Valid @RequestBody EquipamentoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		EquipamentoResponse criado = equipamentoService.cadastrar(request, usuario.id());
		return ResponseEntity.created(URI.create("/api/equipamentos/" + criado.id())).body(criado);
	}

}
