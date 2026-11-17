package com.equiptrack.backend.usuario;

import com.equiptrack.backend.usuario.dto.UsuarioAtualizacaoRequest;
import com.equiptrack.backend.usuario.dto.UsuarioCadastroRequest;
import com.equiptrack.backend.usuario.dto.UsuarioDetalheResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Gestão de usuários, só para desenvolvedores: as rotas exigem o cabeçalho X-Chave-Desenvolvedor com a chave
 * da variável EQUIPTRACK_CHAVE_DESENVOLVEDOR. O token de login, mesmo o do administrador, não dá acesso.
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários (desenvolvedores)")
@SecurityRequirement(name = "chaveDesenvolvedor")
@ApiResponse(responseCode = "401", description = "Chave de desenvolvedor ausente ou errada")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping
	@Operation(summary = "Lista todos os usuários, ativos e inativos, em ordem de nome")
	@ApiResponse(responseCode = "200", description = "Usuários cadastrados")
	public List<UsuarioDetalheResponse> listar() {
		return usuarioService.listar();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Busca um usuário")
	@ApiResponse(responseCode = "200", description = "Usuário encontrado")
	@ApiResponse(responseCode = "404", description = "Usuário inexistente")
	public UsuarioDetalheResponse buscar(@PathVariable Long id) {
		return usuarioService.buscar(id);
	}

	@PostMapping
	@Operation(summary = "Cadastra um usuário com o seu perfil. SOLICITANTE exige os dados de solicitante")
	@ApiResponse(responseCode = "201", description = "Usuário cadastrado")
	@ApiResponse(responseCode = "400", description = "Dados inválidos ou dados de solicitante faltando/sobrando")
	@ApiResponse(responseCode = "409", description = "E-mail ou matrícula já cadastrados")
	public ResponseEntity<UsuarioDetalheResponse> cadastrar(@Valid @RequestBody UsuarioCadastroRequest request) {
		UsuarioDetalheResponse criado = usuarioService.cadastrar(request);
		return ResponseEntity.created(URI.create("/api/usuarios/" + criado.id())).body(criado);
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Atualiza só os campos enviados. \"ativo\": false desativa; nova senha, novo perfil "
			+ "ou desativação encerram as sessões abertas")
	@ApiResponse(responseCode = "200", description = "Usuário atualizado")
	@ApiResponse(responseCode = "400", description = "Dados inválidos ou dados de solicitante faltando/sobrando")
	@ApiResponse(responseCode = "404", description = "Usuário inexistente")
	@ApiResponse(responseCode = "409", description = "E-mail ou matrícula já cadastrados")
	public UsuarioDetalheResponse atualizar(@PathVariable Long id,
			@Valid @RequestBody UsuarioAtualizacaoRequest request) {
		return usuarioService.atualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Exclui um usuário sem registros. Quem já tem histórico deve ser desativado")
	@ApiResponse(responseCode = "204", description = "Usuário excluído")
	@ApiResponse(responseCode = "404", description = "Usuário inexistente")
	@ApiResponse(responseCode = "409", description = "Usuário com registros no histórico, em empréstimos ou em solicitações")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		usuarioService.excluir(id);
		return ResponseEntity.noContent().build();
	}

}
