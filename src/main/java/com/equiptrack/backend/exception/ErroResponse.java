package com.equiptrack.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(Instant timestamp, int status, String erro, String mensagem, String caminho,
		Map<String, String> campos) {

	public static ErroResponse de(HttpStatusCode status, String mensagem, String caminho) {
		return de(status, mensagem, caminho, null);
	}

	public static ErroResponse de(HttpStatusCode status, String mensagem, String caminho,
			Map<String, String> campos) {
		HttpStatus resolvido = HttpStatus.resolve(status.value());
		String erro = resolvido != null ? resolvido.getReasonPhrase() : String.valueOf(status.value());
		return new ErroResponse(Instant.now(), status.value(), erro, mensagem, caminho, campos);
	}

}
