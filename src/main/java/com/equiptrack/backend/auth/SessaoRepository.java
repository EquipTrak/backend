package com.equiptrack.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {

	@Query("""
			select s from Sessao s join fetch s.usuario u
			where s.tokenHash = :tokenHash
			and s.revogadaEm is null
			and s.expiraEm > :agora
			and u.ativo = true
			""")
	Optional<Sessao> buscarValida(@Param("tokenHash") String tokenHash, @Param("agora") Instant agora);

	@Modifying
	@Query("update Sessao s set s.revogadaEm = :agora where s.id = :id and s.revogadaEm is null")
	void revogar(@Param("id") Long id, @Param("agora") Instant agora);

	@Modifying
	@Query("update Sessao s set s.revogadaEm = :agora where s.usuario.id = :usuarioId and s.revogadaEm is null")
	void revogarTodas(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

	@Modifying
	@Query("delete from Sessao s where s.usuario.id = :usuarioId and s.expiraEm <= :agora")
	void removerExpiradas(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

}
