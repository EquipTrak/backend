package com.equiptrack.backend.emprestimo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Regras de atraso (RF13, RN07, RN15). O atraso não é gravado: é calculado na leitura, a partir da data
 * prevista de devolução, no fuso da instituição ({@code equiptrack.fuso-horario}).
 */
@Component
public class Atrasos {

	private final Clock clock;
	private final ZoneId fuso;
	private final EmprestimoRepository emprestimoRepository;

	public Atrasos(Clock clock, @Value("${equiptrack.fuso-horario}") String fuso,
			EmprestimoRepository emprestimoRepository) {
		this.clock = clock;
		this.fuso = ZoneId.of(fuso);
		this.emprestimoRepository = emprestimoRepository;
	}

	/**
	 * RN15: o atraso conta a partir do dia seguinte à data prevista; devolver no próprio dia não é atraso.
	 */
	public static boolean atrasado(Instant dataPrevista, Instant agora, ZoneId fuso) {
		return LocalDate.ofInstant(agora, fuso).isAfter(LocalDate.ofInstant(dataPrevista, fuso));
	}

	/** Status para exibir: um empréstimo ATIVO que passou do dia previsto aparece como ATRASADO (RN07). */
	public StatusEmprestimo statusAtual(Emprestimo emprestimo) {
		if (emprestimo.getStatus() == StatusEmprestimo.ATIVO
				&& atrasado(emprestimo.getDataPrevistaDevolucao(), clock.instant(), fuso)) {
			return StatusEmprestimo.ATRASADO;
		}
		return emprestimo.getStatus();
	}

	/**
	 * RN09: o solicitante tem empréstimo em aberto além do dia previsto. Basta devolver para regularizar,
	 * pois a consulta só olha empréstimos ainda ATIVOS.
	 */
	public boolean solicitanteTemAtraso(Long solicitanteId) {
		return emprestimoRepository.existeAtrasoDoSolicitante(solicitanteId, StatusEmprestimo.ATIVO, inicioDeHoje());
	}

	/** Primeiro instante de hoje no fuso: previsão de devolução antes dele já está atrasada. */
	Instant inicioDeHoje() {
		return LocalDate.ofInstant(clock.instant(), fuso).atStartOfDay(fuso).toInstant();
	}

}
