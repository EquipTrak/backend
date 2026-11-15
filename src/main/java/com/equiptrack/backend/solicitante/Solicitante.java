package com.equiptrack.backend.solicitante;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dados de quem pede equipamentos emprestados. Todo solicitante é um usuário com perfil SOLICITANTE (RF07).
 */
@Entity
@Table(name = "solicitante")
@Getter
@Setter
@NoArgsConstructor
public class Solicitante {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_solicitante")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false, unique = true)
	private Usuario usuario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoSolicitante tipo;

	@Column(nullable = false, unique = true, length = 30)
	private String matricula;

	@Enumerated(EnumType.STRING)
	@Column(name = "situacao_matricula", nullable = false)
	private SituacaoMatricula situacaoMatricula = SituacaoMatricula.ATIVA;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusSolicitante status = StatusSolicitante.ATIVO;

	@Column(length = 20)
	private String telefone;

}
