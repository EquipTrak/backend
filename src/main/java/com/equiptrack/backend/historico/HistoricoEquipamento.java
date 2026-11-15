package com.equiptrack.backend.historico;

import com.equiptrack.backend.emprestimo.Emprestimo;
import com.equiptrack.backend.equipamento.Condicao;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.Situacao;
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
 * Evento do histórico de um equipamento (RF15). Os registros nunca são alterados nem apagados (RN12).
 */
@Entity
@Table(name = "historico_equipamento")
@Getter
@Setter
@NoArgsConstructor
public class HistoricoEquipamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_historico")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_equipamento", nullable = false, updatable = false)
	private Equipamento equipamento;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_emprestimo", updatable = false)
	private Emprestimo emprestimo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false, updatable = false)
	private Usuario usuario;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_evento", nullable = false, updatable = false)
	private TipoEvento tipo;

	@Enumerated(EnumType.STRING)
	@Column(name = "situacao_anterior", length = 20, updatable = false)
	private Situacao situacaoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(name = "situacao_nova", length = 20, updatable = false)
	private Situacao situacaoNova;

	@Enumerated(EnumType.STRING)
	@Column(name = "condicao_anterior", length = 20, updatable = false)
	private Condicao condicaoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(name = "condicao_nova", length = 20, updatable = false)
	private Condicao condicaoNova;

	@Column(length = 500, updatable = false)
	private String observacao;

	@CreationTimestamp
	@Column(name = "data_hora", nullable = false, updatable = false)
	private Instant dataHora;

}
