package com.equiptrack.backend.auth;

import com.equiptrack.backend.exception.LoginBloqueadoException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TentativasLoginTests {

	private static final String CHAVE = "a@b.com|127.0.0.1";

	private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-11-04T12:00:00Z"));
	private final TentativasLogin tentativas = new TentativasLogin(3, Duration.ofMinutes(15), agora::get);

	@Test
	void bloqueiaAoAtingirOLimiteEInformaOTempoRestante() {
		registrarFalhas(2);
		assertThatCode(() -> tentativas.verificar(CHAVE)).doesNotThrowAnyException();

		registrarFalhas(1);
		assertThatThrownBy(() -> tentativas.verificar(CHAVE))
			.isInstanceOfSatisfying(LoginBloqueadoException.class,
					ex -> assertThat(ex.getSegundosParaLiberar()).isEqualTo(901));
	}

	@Test
	void liberaDepoisDoPeriodoDeBloqueio() {
		registrarFalhas(3);
		agora.set(agora.get().plus(Duration.ofMinutes(15)));
		assertThatCode(() -> tentativas.verificar(CHAVE)).doesNotThrowAnyException();

		registrarFalhas(2);
		assertThatCode(() -> tentativas.verificar(CHAVE)).doesNotThrowAnyException();
	}

	@Test
	void limparZeraOContador() {
		registrarFalhas(2);
		tentativas.limpar(CHAVE);
		registrarFalhas(2);
		assertThatCode(() -> tentativas.verificar(CHAVE)).doesNotThrowAnyException();
	}

	@Test
	void chavesDiferentesNaoSeAfetam() {
		registrarFalhas(3);
		assertThatCode(() -> tentativas.verificar("a@b.com|10.0.0.9")).doesNotThrowAnyException();
	}

	private void registrarFalhas(int quantidade) {
		for (int i = 0; i < quantidade; i++) {
			tentativas.registrarFalha(CHAVE);
		}
	}

}
