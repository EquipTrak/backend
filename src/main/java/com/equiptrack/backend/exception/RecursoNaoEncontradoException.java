package com.equiptrack.backend.exception;

/** O recurso pedido não existe. Vira 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

	public RecursoNaoEncontradoException(String mensagem) {
		super(mensagem);
	}

}
