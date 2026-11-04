package com.equiptrack.backend.auth;

import com.equiptrack.backend.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "sessao_usuario")
@Getter
@NoArgsConstructor
public class Sessao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_sessao")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	private Usuario usuario;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "criada_em", nullable = false, updatable = false)
	private Instant criadaEm;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(name = "revogada_em")
	private Instant revogadaEm;

	public Sessao(Usuario usuario, String tokenHash, Instant criadaEm, Instant expiraEm) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.criadaEm = criadaEm;
		this.expiraEm = expiraEm;
	}

}
