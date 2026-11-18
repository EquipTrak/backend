package com.equiptrack.backend.solicitacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoRepository extends JpaRepository<SolicitacaoEmprestimo, Long> {

	boolean existsBySolicitanteIdAndEquipamentoIdAndStatus(Long solicitanteId, Long equipamentoId,
			StatusSolicitacao status);

}
