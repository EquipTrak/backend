package com.equiptrack.backend.emprestimo;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AtrasosTests {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	// Previsto para 14/11 às 23:00 em São Paulo, que em UTC já é 15/11.
	private static final Instant PREVISTA = Instant.parse("2026-11-15T02:00:00Z");

	@Test
	void devolverNoProprioDiaNaoEhAtraso() {
		assertThat(Atrasos.atrasado(PREVISTA, Instant.parse("2026-11-15T02:59:00Z"), SAO_PAULO)).isFalse();
		// Mesmo depois do horário previsto, ainda no dia 14 em São Paulo.
		assertThat(Atrasos.atrasado(PREVISTA, Instant.parse("2026-11-15T02:30:00Z"), SAO_PAULO)).isFalse();
	}

	@Test
	void atrasoComecaNoDiaSeguinteNoFusoDaInstituicao() {
		// 15/11 à 00:00 em São Paulo.
		assertThat(Atrasos.atrasado(PREVISTA, Instant.parse("2026-11-15T03:00:00Z"), SAO_PAULO)).isTrue();
		assertThat(Atrasos.atrasado(PREVISTA, Instant.parse("2026-11-20T12:00:00Z"), SAO_PAULO)).isTrue();
	}

	@Test
	void antesDaDataPrevistaNaoEhAtraso() {
		assertThat(Atrasos.atrasado(PREVISTA, Instant.parse("2026-11-14T12:00:00Z"), SAO_PAULO)).isFalse();
	}

}
