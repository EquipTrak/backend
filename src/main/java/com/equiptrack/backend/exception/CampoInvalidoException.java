package com.equiptrack.backend.exception;

import lombok.Getter;

/** Valor que passa na validação do formato mas viola uma regra do negócio. Vira 400 com o campo indicado. */
@Getter
public class CampoInvalidoException extends RuntimeException {

	private final String campo;

	public CampoInvalidoException(String campo, String mensagem) {
		super(mensagem);
		this.campo = campo;
	}

}
