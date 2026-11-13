package com.equiptrack.backend.auth;

import com.equiptrack.backend.exception.LoginBloqueadoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Limita as falhas de login por e-mail e IP. Fica em memória: basta para o monólito, com uma instância,
 * e o contador zera ao reiniciar a aplicação.
 */
@Component
public class TentativasLogin {

	private static final int LIMITE_ENTRADAS_ANTES_DA_LIMPEZA = 10_000;

	private record Registro(int falhas, Instant expiraEm) {
	}

	private final Map<String, Registro> registros = new ConcurrentHashMap<>();
	private final int maxTentativas;
	private final Duration bloqueio;
	private final Supplier<Instant> relogio;

	@Autowired
	public TentativasLogin(@Value("${equiptrack.auth.login.max-tentativas}") int maxTentativas,
			@Value("${equiptrack.auth.login.bloqueio}") Duration bloqueio) {
		this(maxTentativas, bloqueio, Instant::now);
	}

	TentativasLogin(int maxTentativas, Duration bloqueio, Supplier<Instant> relogio) {
		this.maxTentativas = maxTentativas;
		this.bloqueio = bloqueio;
		this.relogio = relogio;
	}

	public void verificar(String chave) {
		Instant agora = relogio.get();
		Registro registro = registros.get(chave);
		if (registro == null) {
			return;
		}
		if (!registro.expiraEm().isAfter(agora)) {
			registros.remove(chave, registro);
			return;
		}
		if (registro.falhas() >= maxTentativas) {
			long segundos = Duration.between(agora, registro.expiraEm()).toSeconds() + 1;
			throw new LoginBloqueadoException(segundos);
		}
	}

	public void registrarFalha(String chave) {
		Instant agora = relogio.get();
		if (registros.size() >= LIMITE_ENTRADAS_ANTES_DA_LIMPEZA) {
			registros.values().removeIf(registro -> !registro.expiraEm().isAfter(agora));
		}
		registros.compute(chave, (k, atual) -> {
			if (atual == null || !atual.expiraEm().isAfter(agora)) {
				return new Registro(1, agora.plus(bloqueio));
			}
			return new Registro(atual.falhas() + 1, atual.expiraEm());
		});
	}

	public void limpar(String chave) {
		registros.remove(chave);
	}

	void limparTudo() {
		registros.clear();
	}

}
