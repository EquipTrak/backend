package com.equiptrack.backend.exception;

import lombok.Getter;

@Getter
public class LoginBloqueadoException extends RuntimeException {

	private final long segundosParaLiberar;

	public LoginBloqueadoException(long segundosParaLiberar) {
		super("Muitas tentativas de login. Tente novamente em alguns minutos.");
		this.segundosParaLiberar = segundosParaLiberar;
	}

}
