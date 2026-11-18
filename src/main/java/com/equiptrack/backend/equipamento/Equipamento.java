package com.equiptrack.backend.equipamento;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipamento")
@Getter
@Setter
@NoArgsConstructor
public class Equipamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_equipamento")
	private Long id;

	@Column(nullable = false, unique = true, length = 30)
	private String patrimonio;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_categoria", nullable = false)
	private Categoria categoria;

	@Column(nullable = false, length = 255)
	private String descricao;

	@Column(length = 80)
	private String marca;

	@Column(length = 80)
	private String modelo;

	@Column(name = "numero_serie", length = 80)
	private String numeroSerie;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Condicao condicao = Condicao.OTIMA;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Situacao situacao = Situacao.DISPONIVEL;

	@Column(name = "prazo_max_horas", nullable = false)
	private Integer prazoMaxHoras;

	@Column(nullable = false)
	private boolean ativo = true;

	@CreationTimestamp
	@Column(name = "criado_em", nullable = false, updatable = false)
	private LocalDateTime criadoEm;

	@UpdateTimestamp
	@Column(name = "atualizado_em", nullable = false)
	private LocalDateTime atualizadoEm;

}
