package com.equiptrack.backend.exception;

/** O usuário está logado, mas uma regra de negócio impede a ação. Vira 403 com a mensagem informada. */
public class OperacaoNaoPermitidaException extends RuntimeException {

	public OperacaoNaoPermitidaException(String mensagem) {
		super(mensagem);
	}

}
