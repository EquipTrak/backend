package com.equiptrack.backend.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"equiptrack.dev.admin-email=admin-dev@equiptrack.local",
		"equiptrack.dev.admin-senha=senha-dev",
		"spring.datasource.url=jdbc:h2:mem:admin-dev-tests" })
@ActiveProfiles("dev")
class AdminDevInitializerTests {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void criaAdministradorComSenhaEmHash() {
		Usuario admin = usuarioRepository.findByEmailIgnoreCase("admin-dev@equiptrack.local").orElseThrow();
		assertThat(admin.getPerfil()).isEqualTo(Perfil.ADMINISTRADOR);
		assertThat(admin.getSenhaHash()).isNotEqualTo("senha-dev");
		assertThat(passwordEncoder.matches("senha-dev", admin.getSenhaHash())).isTrue();
	}

}
