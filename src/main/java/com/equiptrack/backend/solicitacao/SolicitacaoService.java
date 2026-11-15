package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.emprestimo.Atrasos;
import com.equiptrack.backend.emprestimo.Emprestimo;
import com.equiptrack.backend.emprestimo.EmprestimoRepository;
import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.exception.CampoInvalidoException;
import com.equiptrack.backend.exception.ConflitoException;
import com.equiptrack.backend.exception.OperacaoNaoPermitidaException;
import com.equiptrack.backend.exception.RecursoNaoEncontradoException;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoRequest;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoResponse;
import com.equiptrack.backend.solicitante.SituacaoMatricula;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.equiptrack.backend.solicitante.StatusSolicitante;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SolicitacaoService {

	/**
	 * Folga para o início previsto: o formulário trabalha em minutos, então "agora" já chega alguns
	 * segundos atrasado ao servidor.
	 */
	static final Duration TOLERANCIA_INICIO = Duration.ofMinutes(5);

	private final SolicitacaoRepository solicitacaoRepository;
	private final SolicitanteRepository solicitanteRepository;
	private final EquipamentoRepository equipamentoRepository;
	private final UsuarioRepository usuarioRepository;
	private final EmprestimoRepository emprestimoRepository;
	private final Atrasos atrasos;

	public SolicitacaoService(SolicitacaoRepository solicitacaoRepository,
			SolicitanteRepository solicitanteRepository, EquipamentoRepository equipamentoRepository,
			UsuarioRepository usuarioRepository, EmprestimoRepository emprestimoRepository, Atrasos atrasos) {
		this.emprestimoRepository = emprestimoRepository;
		this.atrasos = atrasos;
		this.solicitacaoRepository = solicitacaoRepository;
		this.solicitanteRepository = solicitanteRepository;
		this.equipamentoRepository = equipamentoRepository;
		this.usuarioRepository = usuarioRepository;
	}

	/** Cria a solicitação do usuário logado, sempre como PENDENTE (RF08). */
	@Transactional
	public SolicitacaoResponse criar(SolicitacaoRequest request, Long usuarioId) {
		Solicitante solicitante = solicitanteRepository.findByUsuarioId(usuarioId)
			.orElseThrow(() -> new OperacaoNaoPermitidaException(
					"Seu usuário não tem cadastro de solicitante. Procure o operador."));
		if (solicitante.getStatus() == StatusSolicitante.BLOQUEADO) {
			throw new OperacaoNaoPermitidaException("Seu cadastro está bloqueado para novas solicitações.");
		}
		if (solicitante.getSituacaoMatricula() == SituacaoMatricula.INATIVA) {
			throw new OperacaoNaoPermitidaException("Sua matrícula está inativa.");
		}
		// RF14 e RN09: com atraso, novas solicitações ficam bloqueadas até a devolução.
		if (atrasos.solicitanteTemAtraso(solicitante.getId())) {
			throw new OperacaoNaoPermitidaException(
					"Você tem um empréstimo em atraso. Devolva o equipamento para fazer novas solicitações.");
		}

		// RN10: período válido.
		Instant inicio = request.inicioPrevisto();
		Instant fim = request.fimPrevisto();
		if (inicio.isBefore(Instant.now().minus(TOLERANCIA_INICIO))) {
			throw new CampoInvalidoException("inicioPrevisto", "O início não pode estar no passado.");
		}
		if (!fim.isAfter(inicio)) {
			throw new CampoInvalidoException("fimPrevisto", "O fim deve ser posterior ao início.");
		}

		Equipamento equipamento = equipamentoRepository.findById(request.equipamentoId())
			.filter(Equipamento::isAtivo)
			.orElseThrow(() -> new CampoInvalidoException("equipamentoId", "Equipamento não encontrado."));
		if (Duration.between(inicio, fim).compareTo(Duration.ofHours(equipamento.getPrazoMaxHoras())) > 0) {
			throw new CampoInvalidoException("fimPrevisto",
					"O prazo máximo deste equipamento é de %d horas.".formatted(equipamento.getPrazoMaxHoras()));
		}
		// RN02: só equipamento disponível pode ser pedido.
		if (equipamento.getSituacao() != Situacao.DISPONIVEL) {
			throw new ConflitoException("equipamentoId", "Este equipamento não está disponível.");
		}
		if (solicitacaoRepository.existsBySolicitanteIdAndEquipamentoIdAndStatus(solicitante.getId(),
				equipamento.getId(), StatusSolicitacao.PENDENTE)) {
			throw new ConflitoException("equipamentoId",
					"Você já tem uma solicitação pendente para este equipamento.");
		}

		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(solicitante);
		solicitacao.setEquipamento(equipamento);
		solicitacao.setDataInicioPrevista(inicio);
		solicitacao.setDataFimPrevista(fim);
		return SolicitacaoResponse.de(solicitacaoRepository.save(solicitacao));
	}

	/** Solicitações do usuário logado, da mais recente para a mais antiga (RF18). */
	@Transactional(readOnly = true)
	public List<SolicitacaoResponse> listarMinhas(Long usuarioId) {
		return comEmprestimos(solicitacaoRepository.buscarDoUsuario(usuarioId));
	}

	/**
	 * Cancela a própria solicitação enquanto ela estiver PENDENTE (RF23). A solicitação de outra pessoa
	 * responde como inexistente, para não revelar que existe.
	 */
	@Transactional
	public SolicitacaoResponse cancelar(Long id, Long usuarioId) {
		SolicitacaoEmprestimo solicitacao = solicitacaoRepository.buscarDoUsuarioPorId(id, usuarioId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada."));
		if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
			throw new ConflitoException("status", "Só é possível cancelar uma solicitação pendente.");
		}
		solicitacao.setStatus(StatusSolicitacao.CANCELADA);
		// RN11: o momento do cancelamento fica em data_decisao; id_operador_decisao segue nulo.
		solicitacao.setDataDecisao(Instant.now());
		return SolicitacaoResponse.de(solicitacao);
	}

	/** Fila do operador (RF09), com filtro opcional de status, da mais antiga para a mais recente. */
	@Transactional(readOnly = true)
	public List<SolicitacaoResponse> listarFila(StatusSolicitacao status) {
		return comEmprestimos(solicitacaoRepository.buscarFila(status));
	}

	/**
	 * Aprova a solicitação pendente (RF09). Aprovar não é retirar (RN03): o equipamento continua com a
	 * situação que tem até a retirada.
	 */
	@Transactional
	public SolicitacaoResponse aprovar(Long id, Long operadorId) {
		SolicitacaoEmprestimo solicitacao = buscarPendente(id);
		if (solicitacao.getSolicitante().getStatus() == StatusSolicitante.BLOQUEADO) {
			throw new ConflitoException("solicitante", "O solicitante está bloqueado e não pode ter solicitações aprovadas.");
		}
		// RF14: solicitante com atraso não tem aprovação até regularizar (RN09).
		if (atrasos.solicitanteTemAtraso(solicitacao.getSolicitante().getId())) {
			throw new ConflitoException("solicitante",
					"O solicitante tem um empréstimo em atraso e só pode ter aprovações depois de devolvê-lo.");
		}

		// A trava faz duas aprovações simultâneas do mesmo equipamento rodarem em fila, então a
		// verificação de sobreposição abaixo enxerga a primeira (RN13 sob concorrência).
		Equipamento equipamento = equipamentoRepository.travarPorId(solicitacao.getEquipamento().getId())
			.orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento não encontrado."));
		if (!equipamento.isAtivo() || equipamento.getSituacao() == Situacao.DANIFICADO
				|| equipamento.getSituacao() == Situacao.EM_MANUTENCAO) {
			throw new ConflitoException("equipamentoId",
					"Este equipamento está indisponível (danificado, em manutenção ou inativo).");
		}
		if (solicitacaoRepository.existeSobreposta(equipamento.getId(), StatusSolicitacao.APROVADA,
				solicitacao.getId(), solicitacao.getDataInicioPrevista(), solicitacao.getDataFimPrevista())) {
			throw new ConflitoException("periodo",
					"Já existe uma solicitação aprovada para este equipamento em um período que se sobrepõe a este.");
		}

		solicitacao.setStatus(StatusSolicitacao.APROVADA);
		registrarDecisao(solicitacao, operadorId);
		return SolicitacaoResponse.de(solicitacao);
	}

	/** Rejeita a solicitação pendente, com o motivo informado pelo operador (RF09). */
	@Transactional
	public SolicitacaoResponse rejeitar(Long id, String motivo, Long operadorId) {
		SolicitacaoEmprestimo solicitacao = buscarPendente(id);
		solicitacao.setStatus(StatusSolicitacao.REJEITADA);
		solicitacao.setMotivoRejeicao(motivo.trim());
		registrarDecisao(solicitacao, operadorId);
		return SolicitacaoResponse.de(solicitacao);
	}

	/** Junta a cada solicitação o seu empréstimo, se a retirada já foi registrada, com uma só consulta. */
	private List<SolicitacaoResponse> comEmprestimos(List<SolicitacaoEmprestimo> solicitacoes) {
		if (solicitacoes.isEmpty()) {
			return List.of();
		}
		Map<Long, Emprestimo> porSolicitacao = emprestimoRepository
			.buscarPorSolicitacoes(solicitacoes.stream().map(SolicitacaoEmprestimo::getId).toList())
			.stream()
			.collect(Collectors.toMap(e -> e.getSolicitacao().getId(), Function.identity()));
		return solicitacoes.stream().map(s -> {
			Emprestimo emprestimo = porSolicitacao.get(s.getId());
			return SolicitacaoResponse.de(s, emprestimo, emprestimo == null ? null : atrasos.statusAtual(emprestimo));
		}).toList();
	}

	private SolicitacaoEmprestimo buscarPendente(Long id) {
		SolicitacaoEmprestimo solicitacao = solicitacaoRepository.buscarPorId(id)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada."));
		if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
			throw new ConflitoException("status", "Esta solicitação não está mais pendente.");
		}
		return solicitacao;
	}

	/** RN11: quem decidiu e quando. */
	private void registrarDecisao(SolicitacaoEmprestimo solicitacao, Long operadorId) {
		solicitacao.setOperadorDecisao(usuarioRepository.getReferenceById(operadorId));
		solicitacao.setDataDecisao(Instant.now());
	}

}
