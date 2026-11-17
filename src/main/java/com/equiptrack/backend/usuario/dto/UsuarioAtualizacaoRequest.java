package com.equiptrack.backend.usuario.dto;

import com.equiptrack.backend.usuario.Perfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Atualização parcial de usuário: só os campos enviados mudam. {@code ativo = false} desativa o usuário,
 * que deixa de entrar no sistema. {@code solicitante} substitui os dados de solicitante e exige o perfil SOLICITANTE.
 */
public record UsuarioAtualizacaoRequest(

		@Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
		@Pattern(regexp = ".*\\S.*", message = "O nome não pode ficar em branco.")
		String nome,

		@Email(message = "Informe um e-mail válido.")
		@Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
		@Pattern(regexp = ".*\\S.*", message = "O e-mail não pode ficar em branco.")
		String email,

		@Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
		String senha,

		Perfil perfil,

		Boolean ativo,

		@Valid
		DadosSolicitanteRequest solicitante) {
}
