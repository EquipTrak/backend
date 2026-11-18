package com.equiptrack.backend.solicitacao.dto;

import com.equiptrack.backend.solicitacao.SolicitacaoEmprestimo;
import com.equiptrack.backend.solicitacao.StatusSolicitacao;

import java.time.Instant;

public record SolicitacaoResponse(Long id, SolicitanteResumo solicitante, EquipamentoResumo equipamento,
		Instant dataSolicitacao, Instant inicioPrevisto, Instant fimPrevisto, StatusSolicitacao status,
		String motivoRejeicao) {

	public record SolicitanteResumo(Long id, String nome) {
	}

	public record EquipamentoResumo(Long id, String patrimonio, String descricao) {
	}

	public static SolicitacaoResponse de(SolicitacaoEmprestimo solicitacao) {
		return new SolicitacaoResponse(solicitacao.getId(),
				new SolicitanteResumo(solicitacao.getSolicitante().getId(),
						solicitacao.getSolicitante().getUsuario().getNome()),
				new EquipamentoResumo(solicitacao.getEquipamento().getId(),
						solicitacao.getEquipamento().getPatrimonio(), solicitacao.getEquipamento().getDescricao()),
				solicitacao.getDataSolicitacao(), solicitacao.getDataInicioPrevista(),
				solicitacao.getDataFimPrevista(), solicitacao.getStatus(), solicitacao.getMotivoRejeicao());
	}

}
