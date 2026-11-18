package com.equiptrack.backend.emprestimo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmprestimoRepository extends JpaRepository<Emprestimo, Long> {

	boolean existsBySolicitacaoId(Long solicitacaoId);

	/** RN04: empréstimo em aberto do equipamento. */
	boolean existsByEquipamentoIdAndAtivoFlagTrue(Long equipamentoId);

	Optional<Emprestimo> findBySolicitacaoId(Long solicitacaoId);

	@Query("select e from Emprestimo e where e.solicitacao.id in :solicitacaoIds")
	List<Emprestimo> buscarPorSolicitacoes(@Param("solicitacaoIds") Collection<Long> solicitacaoIds);

}
