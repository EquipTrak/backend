package com.equiptrack.backend.solicitante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SolicitanteRepository extends JpaRepository<Solicitante, Long> {

	Optional<Solicitante> findByUsuarioId(Long usuarioId);

	boolean existsByMatricula(String matricula);

	Optional<Solicitante> findByMatricula(String matricula);

}
