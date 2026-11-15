package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Pedido de empréstimo de um único equipamento por um período (RF08). O empréstimo em si só nasce na
 * retirada (RN03).
 */
@Entity
@Table(name = "solicitacao_emprestimo")
@Getter
@Setter
@NoArgsConstructor
public class SolicitacaoEmprestimo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_solicitacao")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_solicitante", nullable = false, updatable = false)
	private Solicitante solicitante;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_equipamento", nullable = false, updatable = false)
	private Equipamento equipamento;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_operador_decisao")
	private Usuario operadorDecisao;

	@CreationTimestamp
	@Column(name = "data_solicitacao", nullable = false, updatable = false)
	private Instant dataSolicitacao;

	@Column(name = "data_inicio_prevista", nullable = false)
	private Instant dataInicioPrevista;

	@Column(name = "data_fim_prevista", nullable = false)
	private Instant dataFimPrevista;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

	@Column(name = "data_decisao")
	private Instant dataDecisao;

	@Column(name = "motivo_rejeicao", length = 255)
	private String motivoRejeicao;

}
