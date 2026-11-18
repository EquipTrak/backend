package com.equiptrack.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaveDesenvolvedorConfiguracaoTests {

	@Test
	void chaveCurtaImpedeAInicializacao() {
		assertThatThrownBy(() -> SecurityConfig.validarChaveDesenvolvedor("curta"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("pelo menos 16");
		assertThatThrownBy(() -> SecurityConfig.validarChaveDesenvolvedor("a".repeat(15)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void chaveVaziaOuLongaEhAceita() {
		assertThatCode(() -> SecurityConfig.validarChaveDesenvolvedor("")).doesNotThrowAnyException();
		assertThatCode(() -> SecurityConfig.validarChaveDesenvolvedor("   ")).doesNotThrowAnyException();
		assertThatCode(() -> SecurityConfig.validarChaveDesenvolvedor(null)).doesNotThrowAnyException();
		assertThatCode(() -> SecurityConfig.validarChaveDesenvolvedor("a".repeat(16))).doesNotThrowAnyException();
	}

}
