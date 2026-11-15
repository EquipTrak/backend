package com.equiptrack.backend.equipamento.dto;

import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.Situacao;

public record EquipamentoResponse(Long id, String patrimonio, String descricao, CategoriaResponse categoria,
		String marca, String modelo, String numeroSerie, Condicao condicao, Situacao situacao,
		Integer prazoMaxHoras) {

	public static EquipamentoResponse de(Equipamento equipamento) {
		return new EquipamentoResponse(equipamento.getId(), equipamento.getPatrimonio(), equipamento.getDescricao(),
				CategoriaResponse.de(equipamento.getCategoria()), equipamento.getMarca(), equipamento.getModelo(),
				equipamento.getNumeroSerie(), equipamento.getCondicao(), equipamento.getSituacao(),
				equipamento.getPrazoMaxHoras());
	}

}
