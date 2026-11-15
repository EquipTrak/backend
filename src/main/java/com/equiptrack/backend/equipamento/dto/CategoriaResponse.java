package com.equiptrack.backend.equipamento.dto;

import com.equiptrack.backend.equipamento.Categoria;

public record CategoriaResponse(Long id, String nome) {

	public static CategoriaResponse de(Categoria categoria) {
		return new CategoriaResponse(categoria.getId(), categoria.getNome());
	}

}
