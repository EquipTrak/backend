package com.equiptrack.backend.solicitacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoRepository extends JpaRepository<SolicitacaoEmprestimo, Long> {

	boolean existsBySolicitanteIdAndEquipamentoIdAndStatus(Long solicitanteId, Long equipamentoId,
			StatusSolicitacao status);

	/** Solicitações feitas pelo usuário (como solicitante) ou decididas por ele (como operador). */
	@Query("""
			select count(s) > 0 from SolicitacaoEmprestimo s
			where s.solicitante.usuario.id = :usuarioId or s.operadorDecisao.id = :usuarioId""")
	boolean existeRegistroDoUsuario(@Param("usuarioId") Long usuarioId);

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

	/** Fila do operador: da mais antiga para a mais recente, para atender na ordem de chegada. */
	@Query("""
			select s from SolicitacaoEmprestimo s
			join fetch s.solicitante so join fetch so.usuario join fetch s.equipamento
			where (:status is null or s.status = :status)
			order by s.dataSolicitacao asc, s.id asc""")
	List<SolicitacaoEmprestimo> buscarFila(@Param("status") StatusSolicitacao status);

	@Query("""
			select s from SolicitacaoEmprestimo s
			join fetch s.solicitante so join fetch so.usuario join fetch s.equipamento
			where s.id = :id""")
	Optional<SolicitacaoEmprestimo> buscarPorId(@Param("id") Long id);

	/** RN13: outra solicitação com o status dado, do mesmo equipamento, cujo período cruza o informado. */
	@Query("""
			select count(s) > 0 from SolicitacaoEmprestimo s
			where s.equipamento.id = :equipamentoId and s.status = :status and s.id <> :ignorarId
			  and s.dataInicioPrevista < :fim and s.dataFimPrevista > :inicio""")
	boolean existeSobreposta(@Param("equipamentoId") Long equipamentoId, @Param("status") StatusSolicitacao status,
			@Param("ignorarId") Long ignorarId, @Param("inicio") java.time.Instant inicio,
			@Param("fim") java.time.Instant fim);

}
