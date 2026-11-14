package com.equiptrack.backend.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"equiptrack.dev.senha=senha-dev",
		"spring.datasource.url=jdbc:h2:mem:usuarios-dev-tests" })
@ActiveProfiles("dev")
class UsuariosDevInitializerTests {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UsuariosDevInitializer initializer;

	@Test
	void criaUmUsuarioPorPerfilComSenhaEmHash() {
		assertThat(usuarioRepository.findAll())
			.extracting(Usuario::getEmail, Usuario::getPerfil)
			.containsExactlyInAnyOrder(
					org.assertj.core.groups.Tuple.tuple("admin@equiptrack.local", Perfil.ADMINISTRADOR),
					org.assertj.core.groups.Tuple.tuple("marina.souza@equiptrack.local", Perfil.OPERADOR),
					org.assertj.core.groups.Tuple.tuple("rafael.lima@equiptrack.local", Perfil.SOLICITANTE),
					org.assertj.core.groups.Tuple.tuple("beatriz.alves@equiptrack.local", Perfil.SOLICITANTE));

		Usuario operador = usuarioRepository.findByEmailIgnoreCase("marina.souza@equiptrack.local").orElseThrow();
		assertThat(operador.getSenhaHash()).isNotEqualTo("senha-dev");
		assertThat(passwordEncoder.matches("senha-dev", operador.getSenhaHash())).isTrue();
	}

	@Test
	void naoDuplicaUsuariosAoRodarDeNovo() {
		initializer.run();
		assertThat(usuarioRepository.count()).isEqualTo(UsuariosDevInitializer.USUARIOS.size());
	}

}
