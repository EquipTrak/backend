package com.equiptrack.backend.exception;

import lombok.Getter;

/** O recurso já existe, como um patrimônio repetido. Vira 409 com o campo indicado. */
@Getter
public class ConflitoException extends RuntimeException {

	private final String campo;

	public ConflitoException(String campo, String mensagem) {
		super(mensagem);
		this.campo = campo;
	}

}
