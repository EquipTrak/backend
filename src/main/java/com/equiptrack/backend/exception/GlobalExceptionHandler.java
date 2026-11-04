package com.equiptrack.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(CredenciaisInvalidasException.class)
	ResponseEntity<ErroResponse> credenciaisInvalidas(CredenciaisInvalidasException ex, HttpServletRequest request) {
		return resposta(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
	}

	@ExceptionHandler(UsuarioInativoException.class)
	ResponseEntity<ErroResponse> usuarioInativo(UsuarioInativoException ex, HttpServletRequest request) {
		return resposta(HttpStatus.FORBIDDEN, ex.getMessage(), request);
	}

	@ExceptionHandler(LoginBloqueadoException.class)
	ResponseEntity<ErroResponse> loginBloqueado(LoginBloqueadoException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
		return ResponseEntity.status(status)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosParaLiberar()))
			.body(ErroResponse.de(status, ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErroResponse> acessoNegado(AccessDeniedException ex, HttpServletRequest request) {
		return resposta(HttpStatus.FORBIDDEN, "Você não tem permissão para esta ação.", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErroResponse> erroInesperado(Exception ex, HttpServletRequest request) {
		log.error("Erro inesperado em {}", request.getRequestURI(), ex);
		return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente mais tarde.", request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> campos = new LinkedHashMap<>();
		for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
			campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
		}
		ErroResponse corpo = ErroResponse.de(status, "Dados inválidos.", caminho(request), campos);
		return ResponseEntity.status(status).headers(headers).body(corpo);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ErroResponse corpo = ErroResponse.de(status, "O corpo da requisição é inválido.", caminho(request));
		return ResponseEntity.status(status).headers(headers).body(corpo);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		HttpStatus resolvido = HttpStatus.resolve(statusCode.value());
		String mensagem = resolvido != null ? resolvido.getReasonPhrase() : "Erro na requisição.";
		ErroResponse corpo = ErroResponse.de(statusCode, mensagem, caminho(request));
		return ResponseEntity.status(statusCode).headers(headers).body(corpo);
	}

	private ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem, HttpServletRequest request) {
		return ResponseEntity.status(status).body(ErroResponse.de(status, mensagem, request.getRequestURI()));
	}

	private String caminho(WebRequest request) {
		return ((ServletWebRequest) request).getRequest().getRequestURI();
	}

}
