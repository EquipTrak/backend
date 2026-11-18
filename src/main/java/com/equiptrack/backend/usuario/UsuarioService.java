package com.equiptrack.backend.usuario;

import com.equiptrack.backend.auth.SessaoRepository;
import com.equiptrack.backend.emprestimo.EmprestimoRepository;
import com.equiptrack.backend.exception.CampoInvalidoException;
import com.equiptrack.backend.exception.ConflitoException;
import com.equiptrack.backend.exception.RecursoNaoEncontradoException;
import com.equiptrack.backend.historico.HistoricoEquipamentoRepository;
import com.equiptrack.backend.solicitacao.SolicitacaoRepository;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.equiptrack.backend.usuario.dto.DadosSolicitanteRequest;
import com.equiptrack.backend.usuario.dto.UsuarioAtualizacaoRequest;
import com.equiptrack.backend.usuario.dto.UsuarioCadastroRequest;
import com.equiptrack.backend.usuario.dto.UsuarioDetalheResponse;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gestão de usuários pelos desenvolvedores, pela API. É assim que entram no sistema todos os usuários
 * além dos de demonstração, cada um com o seu perfil.
 */
@Service
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;
	private final SolicitanteRepository solicitanteRepository;
	private final SessaoRepository sessaoRepository;
	private final HistoricoEquipamentoRepository historicoRepository;
	private final EmprestimoRepository emprestimoRepository;
	private final SolicitacaoRepository solicitacaoRepository;
	private final PasswordEncoder passwordEncoder;

	public UsuarioService(UsuarioRepository usuarioRepository, SolicitanteRepository solicitanteRepository,
			SessaoRepository sessaoRepository, HistoricoEquipamentoRepository historicoRepository,
			EmprestimoRepository emprestimoRepository, SolicitacaoRepository solicitacaoRepository,
			PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.solicitanteRepository = solicitanteRepository;
		this.sessaoRepository = sessaoRepository;
		this.historicoRepository = historicoRepository;
		this.emprestimoRepository = emprestimoRepository;
		this.solicitacaoRepository = solicitacaoRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<UsuarioDetalheResponse> listar() {
		Map<Long, Solicitante> solicitantes = solicitanteRepository.findAll().stream()
			.collect(Collectors.toMap(s -> s.getUsuario().getId(), Function.identity()));
		return usuarioRepository.findAll(Sort.by("nome", "id")).stream()
			.map(u -> UsuarioDetalheResponse.de(u, solicitantes.get(u.getId())))
			.toList();
	}

	@Transactional(readOnly = true)
	public UsuarioDetalheResponse buscar(Long id) {
		return resposta(encontrar(id));
	}

	@Transactional
	public UsuarioDetalheResponse cadastrar(UsuarioCadastroRequest request) {
		String email = normalizarEmail(request.email());
		validarEmailLivre(email, null);
		validarDadosSolicitante(request.perfil(), request.solicitante(), true);

		Usuario usuario = new Usuario();
		usuario.setNome(request.nome().trim());
		usuario.setEmail(email);
		usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
		usuario.setPerfil(request.perfil());
		usuarioRepository.save(usuario);

		if (request.solicitante() != null) {
			salvarSolicitante(usuario, null, request.solicitante());
		}
		return resposta(usuario);
	}

	@Transactional
	public UsuarioDetalheResponse atualizar(Long id, UsuarioAtualizacaoRequest request) {
		Usuario usuario = encontrar(id);
		Solicitante solicitante = solicitanteRepository.findByUsuarioId(id).orElse(null);
		Perfil perfil = request.perfil() != null ? request.perfil() : usuario.getPerfil();
		// Quem passa a ser solicitante precisa dos dados de solicitante, a menos que já os tenha.
		validarDadosSolicitante(perfil, request.solicitante(), solicitante == null);

		boolean encerrarSessoes = false;
		if (request.nome() != null) {
			usuario.setNome(request.nome().trim());
		}
		if (request.email() != null) {
			String email = normalizarEmail(request.email());
			validarEmailLivre(email, id);
			usuario.setEmail(email);
		}
		if (request.senha() != null) {
			usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
			encerrarSessoes = true;
		}
		if (perfil != usuario.getPerfil()) {
			usuario.setPerfil(perfil);
			encerrarSessoes = true;
		}
		if (request.ativo() != null && request.ativo() != usuario.isAtivo()) {
			usuario.setAtivo(request.ativo());
			encerrarSessoes |= !request.ativo();
		}
		if (request.solicitante() != null) {
			solicitante = salvarSolicitante(usuario, solicitante, request.solicitante());
		}
		// Nova senha, novo perfil ou desativação: as sessões abertas param de valer na hora.
		if (encerrarSessoes) {
			sessaoRepository.revogarTodas(id, Instant.now());
		}
		return UsuarioDetalheResponse.de(usuario, solicitante);
	}

	/**
	 * Exclui o usuário que ainda não deixou registros. Quem já aparece no histórico, em empréstimos ou em
	 * solicitações não pode ser apagado (RN08, RN12): nesse caso a API pede a desativação.
	 */
	@Transactional
	public void excluir(Long id) {
		Usuario usuario = encontrar(id);
		if (historicoRepository.existsByUsuarioId(id) || emprestimoRepository.existeRegistroDoOperador(id)
				|| solicitacaoRepository.existeRegistroDoUsuario(id)) {
			throw new ConflitoException("usuario", "O usuário já tem registros no histórico, em empréstimos "
					+ "ou em solicitações e não pode ser excluído. Desative-o com \"ativo\": false.");
		}
		sessaoRepository.removerTodas(id);
		solicitanteRepository.findByUsuarioId(id).ifPresent(solicitanteRepository::delete);
		usuarioRepository.delete(usuario);
	}

	private Usuario encontrar(Long id) {
		return usuarioRepository.findById(id)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
	}

	private UsuarioDetalheResponse resposta(Usuario usuario) {
		return UsuarioDetalheResponse.de(usuario, solicitanteRepository.findByUsuarioId(usuario.getId()).orElse(null));
	}

	private String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private void validarEmailLivre(String email, Long idAtual) {
		usuarioRepository.findByEmailIgnoreCase(email)
			.filter(existente -> !existente.getId().equals(idAtual))
			.ifPresent(existente -> {
				throw new ConflitoException("email", "Já existe um usuário com este e-mail.");
			});
	}

	private void validarDadosSolicitante(Perfil perfil, DadosSolicitanteRequest dados, boolean obrigatorios) {
		if (perfil == Perfil.SOLICITANTE && dados == null && obrigatorios) {
			throw new CampoInvalidoException("solicitante",
					"Informe os dados de solicitante (tipo e matrícula) para o perfil SOLICITANTE.");
		}
		if (perfil != Perfil.SOLICITANTE && dados != null) {
			throw new CampoInvalidoException("solicitante", "Só usuários com perfil SOLICITANTE têm dados de solicitante.");
		}
	}

	private Solicitante salvarSolicitante(Usuario usuario, Solicitante existente, DadosSolicitanteRequest dados) {
		String matricula = dados.matricula().trim();
		solicitanteRepository.findByMatricula(matricula)
			.filter(outro -> existente == null || !outro.getId().equals(existente.getId()))
			.ifPresent(outro -> {
				throw new ConflitoException("solicitante.matricula", "Já existe um solicitante com esta matrícula.");
			});
		Solicitante solicitante = existente != null ? existente : new Solicitante();
		solicitante.setUsuario(usuario);
		solicitante.setTipo(dados.tipo());
		solicitante.setMatricula(matricula);
		solicitante.setTelefone(dados.telefone() == null || dados.telefone().isBlank() ? null : dados.telefone().trim());
		return solicitanteRepository.save(solicitante);
	}

}
