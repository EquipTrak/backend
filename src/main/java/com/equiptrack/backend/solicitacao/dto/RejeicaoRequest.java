package com.equiptrack.backend.solicitacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejeicaoRequest(
		@NotBlank(message = "Informe o motivo da rejeição.")
		@Size(max = 255, message = "O motivo pode ter até 255 caracteres.") String motivo) {
}
