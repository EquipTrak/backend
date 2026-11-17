package com.equiptrack.backend.usuario.dto;

import com.equiptrack.backend.usuario.Perfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro de usuário pelos desenvolvedores. {@code solicitante} só vale, e é obrigatório, para o perfil SOLICITANTE. */
public record UsuarioCadastroRequest(

		@NotBlank(message = "Informe o nome.")
		@Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
		String nome,

		@NotBlank(message = "Informe o e-mail.")
		@Email(message = "Informe um e-mail válido.")
		@Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
		String email,

		@NotBlank(message = "Informe a senha.")
		@Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
		String senha,

		@NotNull(message = "Informe o perfil: SOLICITANTE, OPERADOR ou ADMINISTRADOR.")
		Perfil perfil,

		@Valid
		DadosSolicitanteRequest solicitante) {
}
