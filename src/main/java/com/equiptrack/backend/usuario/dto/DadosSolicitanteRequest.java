package com.equiptrack.backend.usuario.dto;

import com.equiptrack.backend.solicitante.TipoSolicitante;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados de solicitante (RF07), obrigatórios para usuários com perfil SOLICITANTE. */
public record DadosSolicitanteRequest(

		@NotNull(message = "Informe o tipo: ALUNO ou PROFESSOR.")
		TipoSolicitante tipo,

		@NotBlank(message = "Informe a matrícula.")
		@Size(max = 30, message = "A matrícula deve ter no máximo 30 caracteres.")
		String matricula,

		@Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
		String telefone) {
}
