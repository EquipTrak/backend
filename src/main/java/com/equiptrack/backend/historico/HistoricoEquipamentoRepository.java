package com.equiptrack.backend.historico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistoricoEquipamentoRepository extends JpaRepository<HistoricoEquipamento, Long> {

	/** Eventos do equipamento, do mais recente para o mais antigo. */
	@Query("""
			select h from HistoricoEquipamento h join fetch h.usuario
			where h.equipamento.id = :equipamentoId
			order by h.dataHora desc, h.id desc""")
	List<HistoricoEquipamento> buscarPorEquipamento(@Param("equipamentoId") Long equipamentoId);

}
