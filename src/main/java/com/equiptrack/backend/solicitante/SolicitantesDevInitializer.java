package com.equiptrack.backend.solicitante;

import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cria o cadastro de solicitante dos usuários de demonstração com esse perfil, em ambiente de
 * desenvolvimento, pois ainda não há tela de cadastro de solicitantes (RF07). Só cria o que não existe.
 */
@Component
@Profile("dev")
@Order(3) // Depois do UsuariosDevInitializer, que cria os usuários.
public class SolicitantesDevInitializer implements CommandLineRunner {

	private record SolicitanteDemo(String email, TipoSolicitante tipo, String matricula) {
	}

	static final List<SolicitanteDemo> SOLICITANTES = List.of(
			new SolicitanteDemo("rafael.lima@equiptrack.local", TipoSolicitante.ALUNO, "DEMO-0001"),
			new SolicitanteDemo("beatriz.alves@equiptrack.local", TipoSolicitante.PROFESSOR, "DEMO-0002"));

	private final UsuarioRepository usuarioRepository;
	private final SolicitanteRepository solicitanteRepository;

	public SolicitantesDevInitializer(UsuarioRepository usuarioRepository,
			SolicitanteRepository solicitanteRepository) {
		this.usuarioRepository = usuarioRepository;
		this.solicitanteRepository = solicitanteRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		for (SolicitanteDemo demo : SOLICITANTES) {
			// Sem EQUIPTRACK_DEV_SENHA não há usuários de demonstração, e nada é criado.
			Usuario usuario = usuarioRepository.findByEmailIgnoreCase(demo.email()).orElse(null);
			if (usuario == null || solicitanteRepository.findByUsuarioId(usuario.getId()).isPresent()
					|| solicitanteRepository.existsByMatricula(demo.matricula())) {
				continue;
			}
			Solicitante solicitante = new Solicitante();
			solicitante.setUsuario(usuario);
			solicitante.setTipo(demo.tipo());
			solicitante.setMatricula(demo.matricula());
			solicitanteRepository.save(solicitante);
		}
	}

}
