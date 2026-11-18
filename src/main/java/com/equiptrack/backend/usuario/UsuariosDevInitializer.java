package com.equiptrack.backend.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cria usuários de demonstração, um por perfil usado no front, em ambiente de desenvolvimento,
 * pois ainda não há cadastro de usuários. Sem a variável EQUIPTRACK_DEV_SENHA, nada é criado.
 */
@Component
@Profile("dev")
public class UsuariosDevInitializer implements CommandLineRunner {

	private record UsuarioDemo(String nome, String email, Perfil perfil) {
	}

	static final List<UsuarioDemo> USUARIOS = List.of(
			new UsuarioDemo("Administrador", "admin@equiptrack.local", Perfil.ADMINISTRADOR),
			new UsuarioDemo("Marina Souza", "marina.souza@equiptrack.local", Perfil.OPERADOR),
			new UsuarioDemo("Rafael Lima", "rafael.lima@equiptrack.local", Perfil.SOLICITANTE),
			new UsuarioDemo("Beatriz Alves", "beatriz.alves@equiptrack.local", Perfil.SOLICITANTE));

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final String senha;

	public UsuariosDevInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			@Value("${equiptrack.dev.senha}") String senha) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.senha = senha;
	}

	@Override
	public void run(String... args) {
		if (senha.isBlank()) {
			return;
		}
		for (UsuarioDemo demo : USUARIOS) {
			if (usuarioRepository.findByEmailIgnoreCase(demo.email()).isEmpty()) {
				Usuario usuario = new Usuario();
				usuario.setNome(demo.nome());
				usuario.setEmail(demo.email());
				usuario.setSenhaHash(passwordEncoder.encode(senha));
				usuario.setPerfil(demo.perfil());
				usuarioRepository.save(usuario);
			}
		}
	}

}
