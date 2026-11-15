package com.equiptrack.backend.solicitacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoRepository extends JpaRepository<SolicitacaoEmprestimo, Long> {

	boolean existsBySolicitanteIdAndEquipamentoIdAndStatus(Long solicitanteId, Long equipamentoId,
			StatusSolicitacao status);

	/** Solicitações do usuário, da mais recente para a mais antiga. */
	@Query("""
			select s from SolicitacaoEmprestimo s
			join fetch s.solicitante so join fetch so.usuario u join fetch s.equipamento
			where u.id = :usuarioId
			order by s.dataSolicitacao desc, s.id desc""")
	List<SolicitacaoEmprestimo> buscarDoUsuario(@Param("usuarioId") Long usuarioId);

	/** A solicitação, só se pertencer ao usuário. */
	@Query("""
			select s from SolicitacaoEmprestimo s
			join fetch s.solicitante so join fetch so.usuario u join fetch s.equipamento
			where s.id = :id and u.id = :usuarioId""")
	Optional<SolicitacaoEmprestimo> buscarDoUsuarioPorId(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

}
