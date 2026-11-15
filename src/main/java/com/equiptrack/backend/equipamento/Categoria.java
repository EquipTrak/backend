package com.equiptrack.backend.equipamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categoria_equipamento")
@Getter
@Setter
@NoArgsConstructor
public class Categoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_categoria")
	private Long id;

	@Column(nullable = false, unique = true, length = 80)
	private String nome;

	@Column(length = 255)
	private String descricao;

	@Column(nullable = false)
	private boolean ativa = true;

	public Categoria(String nome) {
		this.nome = nome;
	}

}
