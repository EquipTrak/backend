package com.equiptrack.backend.auth;

import com.equiptrack.backend.auth.dto.LoginRequest;
import com.equiptrack.backend.auth.dto.LoginResponse;
import com.equiptrack.backend.auth.dto.UsuarioResponse;
import com.equiptrack.backend.exception.CredenciaisInvalidasException;
import com.equiptrack.backend.exception.UsuarioInativoException;
import com.equiptrack.backend.security.UsuarioAutenticado;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

	private static final int TOKEN_BYTES = 32;

	private final UsuarioRepository usuarioRepository;
	private final SessaoRepository sessaoRepository;
	private final PasswordEncoder passwordEncoder;
	private final TentativasLogin tentativasLogin;
	private final Duration tokenValidade;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String hashFicticio;

	public AuthService(UsuarioRepository usuarioRepository, SessaoRepository sessaoRepository,
			PasswordEncoder passwordEncoder, TentativasLogin tentativasLogin,
			@Value("${equiptrack.auth.token-validade}") Duration tokenValidade) {
		this.usuarioRepository = usuarioRepository;
		this.sessaoRepository = sessaoRepository;
		this.passwordEncoder = passwordEncoder;
		this.tentativasLogin = tentativasLogin;
		this.tokenValidade = tokenValidade;
		this.hashFicticio = passwordEncoder.encode("usuario-inexistente");
	}

	@Transactional
	public LoginResponse login(LoginRequest request, String ip) {
		String chave = request.email().toLowerCase(Locale.ROOT) + "|" + ip;
		tentativasLogin.verificar(chave);

		Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email()).orElse(null);
		// Sem usuário, compara com um hash fictício para gastar o mesmo tempo e não revelar quais e-mails existem.
		String hashEsperado = usuario != null ? usuario.getSenhaHash() : hashFicticio;
		boolean senhaConfere = passwordEncoder.matches(request.senha(), hashEsperado);
		if (usuario == null || !senhaConfere) {
			tentativasLogin.registrarFalha(chave);
			throw new CredenciaisInvalidasException();
		}
		tentativasLogin.limpar(chave);
		if (!usuario.isAtivo()) {
			throw new UsuarioInativoException();
		}

		Instant agora = Instant.now();
		Instant expiraEm = agora.plus(tokenValidade);
		String token = gerarToken();
		sessaoRepository.removerExpiradas(usuario.getId(), agora);
		sessaoRepository.save(new Sessao(usuario, hash(token), agora, expiraEm));
		return new LoginResponse(token, "Bearer", expiraEm, UsuarioResponse.de(usuario));
	}

	@Transactional(readOnly = true)
	public Optional<UsuarioAutenticado> autenticar(String token) {
		return sessaoRepository.buscarValida(hash(token), Instant.now())
				.map(sessao -> UsuarioAutenticado.de(sessao.getId(), sessao.getUsuario()));
	}

	@Transactional
	public void logout(UsuarioAutenticado usuario) {
		sessaoRepository.revogar(usuario.sessaoId(), Instant.now());
	}

	@Transactional
	public void logoutDeTodasAsSessoes(UsuarioAutenticado usuario) {
		sessaoRepository.revogarTodas(usuario.id(), Instant.now());
	}

	private String gerarToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
