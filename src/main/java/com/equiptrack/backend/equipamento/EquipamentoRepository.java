package com.equiptrack.backend.equipamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {

	boolean existsByPatrimonioIgnoreCase(String patrimonio);

	/**
	 * Lista os equipamentos ativos. Cada filtro nulo é ignorado; {@code busca} já chega em minúsculas e
	 * entre {@code %}, e procura no patrimônio e na descrição.
	 */
	@Query("""
			select e from Equipamento e join fetch e.categoria c
			where e.ativo = true
			  and (:situacao is null or e.situacao = :situacao)
			  and (:categoriaId is null or c.id = :categoriaId)
			  and (:busca is null or lower(e.patrimonio) like :busca or lower(e.descricao) like :busca)
			order by e.patrimonio""")
	List<Equipamento> buscar(@Param("situacao") Situacao situacao, @Param("categoriaId") Long categoriaId,
			@Param("busca") String busca);

}
