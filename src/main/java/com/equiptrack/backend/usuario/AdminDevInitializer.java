package com.equiptrack.backend.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro administrador em ambiente de desenvolvimento, pois ainda não há cadastro de usuários.
 * Sem a variável EQUIPTRACK_ADMIN_SENHA, nada é criado.
 */
@Component
@Profile("dev")
public class AdminDevInitializer implements CommandLineRunner {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String senha;

	public AdminDevInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			@Value("${equiptrack.dev.admin-email}") String email,
			@Value("${equiptrack.dev.admin-senha}") String senha) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.senha = senha;
	}

	@Override
	public void run(String... args) {
		if (senha.isBlank() || usuarioRepository.findByEmailIgnoreCase(email).isPresent()) {
			return;
		}
		Usuario admin = new Usuario();
		admin.setNome("Administrador");
		admin.setEmail(email);
		admin.setSenhaHash(passwordEncoder.encode(senha));
		admin.setPerfil(Perfil.ADMINISTRADOR);
		usuarioRepository.save(admin);
	}

}
