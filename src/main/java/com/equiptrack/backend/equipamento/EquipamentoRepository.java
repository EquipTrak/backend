package com.equiptrack.backend.equipamento;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {

	boolean existsByPatrimonioIgnoreCase(String patrimonio);

	java.util.Optional<Equipamento> findByPatrimonioIgnoreCase(String patrimonio);

	long countByAtivoTrueAndSituacao(Situacao situacao);

	long countByAtivoTrue();

	/**
	 * Busca o equipamento travando a linha até o fim da transação, para que duas decisões simultâneas sobre o
	 * mesmo equipamento rodem uma depois da outra (RNF06).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Equipamento e where e.id = :id")
	java.util.Optional<Equipamento> travarPorId(@Param("id") Long id);

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
