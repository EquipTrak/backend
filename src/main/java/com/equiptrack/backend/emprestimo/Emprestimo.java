package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.solicitacao.SolicitacaoEmprestimo;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Empréstimo de um equipamento, criado na retirada de uma solicitação aprovada (RF10, RN03).
 *
 * <p>{@code ativoFlag} vale {@code true} enquanto o empréstimo está em aberto e {@code null} depois da
 * devolução. A restrição única sobre (equipamento, ativoFlag) impede, no próprio banco, dois empréstimos em
 * aberto do mesmo equipamento (RN04, RNF06), pois o banco aceita vários nulos.
 */
@Entity
@Table(name = "emprestimo", uniqueConstraints = @UniqueConstraint(name = "uk_emprestimo_equipamento_ativo",
		columnNames = { "id_equipamento", "ativo_flag" }))
@Getter
@Setter
@NoArgsConstructor
public class Emprestimo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_emprestimo")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_solicitacao", nullable = false, unique = true, updatable = false)
	private SolicitacaoEmprestimo solicitacao;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_equipamento", nullable = false, updatable = false)
	private Equipamento equipamento;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_operador_retirada", nullable = false, updatable = false)
	private Usuario operadorRetirada;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_operador_devolucao")
	private Usuario operadorDevolucao;

	@Column(name = "data_retirada", nullable = false, updatable = false)
	private Instant dataRetirada;

	@Column(name = "data_prevista_devolucao", nullable = false)
	private Instant dataPrevistaDevolucao;

	@Column(name = "data_devolucao")
	private Instant dataDevolucao;

	@Enumerated(EnumType.STRING)
	@Column(name = "condicao_devolucao")
	private Condicao condicaoDevolucao;

	@Column(name = "observacao_devolucao", length = 500)
	private String observacaoDevolucao;

	/** Gravado como ATIVO ou DEVOLVIDO. ATRASADO é calculado na leitura (RN07, RN15). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusEmprestimo status = StatusEmprestimo.ATIVO;

	@Column(name = "ativo_flag")
	private Boolean ativoFlag = Boolean.TRUE;

}
