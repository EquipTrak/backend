package com.equiptrack.backend.emprestimo.dto;

import com.equiptrack.backend.equipamento.Condicao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DevolucaoRequest(
		@NotNull(message = "Informe a condição do equipamento na devolução.") Condicao condicao,
		@Size(max = 500, message = "A observação pode ter até 500 caracteres.") String observacao) {
}
