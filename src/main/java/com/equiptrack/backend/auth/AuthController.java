package com.equiptrack.backend.auth;

import com.equiptrack.backend.auth.dto.LoginRequest;
import com.equiptrack.backend.auth.dto.LoginResponse;
import com.equiptrack.backend.auth.dto.UsuarioResponse;
import com.equiptrack.backend.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	@Operation(summary = "Autentica o usuário e emite um token de acesso")
	@ApiResponse(responseCode = "200", description = "Login realizado")
	@ApiResponse(responseCode = "400", description = "Dados inválidos")
	@ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos")
	@ApiResponse(responseCode = "403", description = "Usuário desativado")
	@ApiResponse(responseCode = "429", description = "Muitas tentativas de login; ver cabeçalho Retry-After")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return authService.login(request, http.getRemoteAddr());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoga o token usado na requisição", security = @SecurityRequirement(name = "bearer"))
	@ApiResponse(responseCode = "204", description = "Token revogado")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	public void logout(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		authService.logout(usuario);
	}

	@PostMapping("/logout-todas")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoga todas as sessões do usuário", security = @SecurityRequirement(name = "bearer"))
	@ApiResponse(responseCode = "204", description = "Sessões revogadas")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	public void logoutDeTodasAsSessoes(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		authService.logoutDeTodasAsSessoes(usuario);
	}

	@GetMapping("/me")
	@Operation(summary = "Retorna o usuário dono do token", security = @SecurityRequirement(name = "bearer"))
	@ApiResponse(responseCode = "200", description = "Usuário autenticado")
	@ApiResponse(responseCode = "401", description = "Token ausente, inválido, expirado ou revogado")
	public UsuarioResponse me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return UsuarioResponse.de(usuario);
	}

}
