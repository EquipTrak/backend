package com.equiptrack.backend.solicitacao.dto;

import com.equiptrack.backend.emprestimo.Emprestimo;
import com.equiptrack.backend.emprestimo.StatusEmprestimo;
import com.equiptrack.backend.solicitacao.SolicitacaoEmprestimo;
import com.equiptrack.backend.solicitacao.StatusSolicitacao;

import java.time.Instant;

/**
 * Solicitação no formato das listas. {@code emprestimo} vem preenchido depois da retirada; o front combina
 * os dois status para exibir (divergência 3 em docs/integracao-frontend.md).
 */
public record SolicitacaoResponse(Long id, SolicitanteResumo solicitante, EquipamentoResumo equipamento,
		Instant dataSolicitacao, Instant inicioPrevisto, Instant fimPrevisto, StatusSolicitacao status,
		String motivoRejeicao, EmprestimoResumo emprestimo) {

	public record SolicitanteResumo(Long id, String nome) {
	}

	public record EquipamentoResumo(Long id, String patrimonio, String descricao) {
	}

	public record EmprestimoResumo(Long id, StatusEmprestimo status, Instant dataRetirada,
			Instant dataPrevistaDevolucao, Instant dataDevolucao) {

		/** {@code statusAtual} é o status para exibir, com o atraso já calculado (ver Atrasos). */
		public static EmprestimoResumo de(Emprestimo emprestimo, StatusEmprestimo statusAtual) {
			return new EmprestimoResumo(emprestimo.getId(), statusAtual, emprestimo.getDataRetirada(),
					emprestimo.getDataPrevistaDevolucao(), emprestimo.getDataDevolucao());
		}

	}

	/** Solicitação ainda sem empréstimo. */
	public static SolicitacaoResponse de(SolicitacaoEmprestimo solicitacao) {
		return de(solicitacao, null);
	}

	public static SolicitacaoResponse de(SolicitacaoEmprestimo solicitacao, Emprestimo emprestimo) {
		return de(solicitacao, emprestimo, emprestimo == null ? null : emprestimo.getStatus());
	}

	public static SolicitacaoResponse de(SolicitacaoEmprestimo solicitacao, Emprestimo emprestimo,
			StatusEmprestimo statusAtual) {
		return new SolicitacaoResponse(solicitacao.getId(),
				new SolicitanteResumo(solicitacao.getSolicitante().getId(),
						solicitacao.getSolicitante().getUsuario().getNome()),
				new EquipamentoResumo(solicitacao.getEquipamento().getId(),
						solicitacao.getEquipamento().getPatrimonio(), solicitacao.getEquipamento().getDescricao()),
				solicitacao.getDataSolicitacao(), solicitacao.getDataInicioPrevista(),
				solicitacao.getDataFimPrevista(), solicitacao.getStatus(), solicitacao.getMotivoRejeicao(),
				emprestimo == null ? null : EmprestimoResumo.de(emprestimo, statusAtual));
	}

}
