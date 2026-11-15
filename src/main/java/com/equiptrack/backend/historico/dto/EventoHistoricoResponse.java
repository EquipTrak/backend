package com.equiptrack.backend.historico.dto;

import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoEquipamento;
import com.equiptrack.backend.historico.TipoEvento;

import java.time.Instant;

public record EventoHistoricoResponse(Long id, Instant dataHora, TipoEvento tipo, UsuarioResumo usuario,
		Situacao situacaoAnterior, Situacao situacaoNova, Condicao condicaoAnterior, Condicao condicaoNova,
		String observacao, Long emprestimoId) {

	public record UsuarioResumo(Long id, String nome) {
	}

	public static EventoHistoricoResponse de(HistoricoEquipamento evento) {
		return new EventoHistoricoResponse(evento.getId(), evento.getDataHora(), evento.getTipo(),
				new UsuarioResumo(evento.getUsuario().getId(), evento.getUsuario().getNome()),
				evento.getSituacaoAnterior(), evento.getSituacaoNova(), evento.getCondicaoAnterior(),
				evento.getCondicaoNova(), evento.getObservacao(), evento.getEmprestimoId());
	}

}
