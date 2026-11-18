package com.equiptrack.backend.solicitacao.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SolicitacaoRequest(
		@NotNull(message = "Informe o equipamento.") Long equipamentoId,
		@NotNull(message = "Informe a data e hora de início.") Instant inicioPrevisto,
		@NotNull(message = "Informe a data e hora de fim.") Instant fimPrevisto) {
}
