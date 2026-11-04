package com.equiptrack.backend.exception;

public class UsuarioInativoException extends RuntimeException {

	public UsuarioInativoException() {
		super("Usuário desativado. Procure o administrador.");
	}

}
