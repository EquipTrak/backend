package com.equiptrack.backend.equipamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

	List<Categoria> findByAtivaTrueOrderByNomeAsc();

	Optional<Categoria> findByNomeIgnoreCase(String nome);

}
