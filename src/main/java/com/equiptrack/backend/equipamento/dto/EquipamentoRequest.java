package com.equiptrack.backend.equipamento.dto;

import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Situacao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de equipamento (RF04). {@code situacao} é opcional e vale DISPONIVEL quando omitida.
 */
public record EquipamentoRequest(

		@NotBlank(message = "Informe o identificador patrimonial.")
		@Size(max = 30, message = "O patrimônio deve ter no máximo 30 caracteres.")
		@Pattern(regexp = "\\s*[A-Za-z0-9-]*\\s*", message = "Use apenas letras, números e hífen. Ex.: NB-0003.")
		String patrimonio,

		@NotNull(message = "Selecione uma categoria.")
		Long categoriaId,

		@NotBlank(message = "Descreva o equipamento.")
		@Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
		String descricao,

		@Size(max = 80, message = "A marca deve ter no máximo 80 caracteres.")
		String marca,

		@Size(max = 80, message = "O modelo deve ter no máximo 80 caracteres.")
		String modelo,

		@Size(max = 80, message = "O número de série deve ter no máximo 80 caracteres.")
		String numeroSerie,

		@NotNull(message = "Informe a condição.")
		Condicao condicao,

		Situacao situacao,

		@NotNull(message = "Informe o prazo máximo de empréstimo em horas.")
		@Min(value = 1, message = "O prazo máximo deve ser de pelo menos 1 hora.")
		@Max(value = 720, message = "O prazo máximo deve ser de no máximo 720 horas (30 dias).")
		Integer prazoMaxHoras) {
}
