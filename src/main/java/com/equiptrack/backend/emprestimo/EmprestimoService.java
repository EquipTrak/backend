package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.exception.ConflitoException;
import com.equiptrack.backend.exception.RecursoNaoEncontradoException;
import com.equiptrack.backend.historico.HistoricoService;
import com.equiptrack.backend.solicitacao.SolicitacaoEmprestimo;
import com.equiptrack.backend.solicitacao.SolicitacaoRepository;
import com.equiptrack.backend.solicitacao.StatusSolicitacao;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoResponse;
import com.equiptrack.backend.solicitante.StatusSolicitante;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EmprestimoService {

	private final EmprestimoRepository emprestimoRepository;
	private final SolicitacaoRepository solicitacaoRepository;
	private final EquipamentoRepository equipamentoRepository;
	private final UsuarioRepository usuarioRepository;
	private final HistoricoService historicoService;

	public EmprestimoService(EmprestimoRepository emprestimoRepository, SolicitacaoRepository solicitacaoRepository,
			EquipamentoRepository equipamentoRepository, UsuarioRepository usuarioRepository,
			HistoricoService historicoService) {
		this.emprestimoRepository = emprestimoRepository;
		this.solicitacaoRepository = solicitacaoRepository;
		this.equipamentoRepository = equipamentoRepository;
		this.usuarioRepository = usuarioRepository;
		this.historicoService = historicoService;
	}

	/**
	 * Registra a entrega do equipamento ao solicitante (RF10): cria o empréstimo, passa o equipamento a
	 * EMPRESTADO e grava o evento no histórico, tudo na mesma transação (RN11).
	 */
	@Transactional
	public SolicitacaoResponse registrarRetirada(Long solicitacaoId, Long operadorId) {
		SolicitacaoEmprestimo solicitacao = solicitacaoRepository.buscarPorId(solicitacaoId)
			.orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada."));
		if (solicitacao.getStatus() != StatusSolicitacao.APROVADA) {
			throw new ConflitoException("status", "Só é possível registrar a retirada de uma solicitação aprovada.");
		}
		if (emprestimoRepository.existsBySolicitacaoId(solicitacaoId)) {
			throw new ConflitoException("status", "A retirada desta solicitação já foi registrada.");
		}
		if (solicitacao.getSolicitante().getStatus() == StatusSolicitante.BLOQUEADO) {
			throw new ConflitoException("solicitante", "O solicitante está bloqueado e não pode retirar equipamentos.");
		}
		Instant agora = Instant.now();
		if (!agora.isBefore(solicitacao.getDataFimPrevista())) {
			throw new ConflitoException("periodo", "O período desta solicitação já terminou. Peça uma nova solicitação.");
		}

		// A trava faz duas retiradas simultâneas do mesmo equipamento rodarem em fila (RN04, RNF06).
		Equipamento equipamento = equipamentoRepository.travarPorId(solicitacao.getEquipamento().getId())
			.orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento não encontrado."));
		// RN02: só sai equipamento DISPONIVEL; RN04: nunca dois empréstimos em aberto.
		if (equipamento.getSituacao() != Situacao.DISPONIVEL
				|| emprestimoRepository.existsByEquipamentoIdAndAtivoFlagTrue(equipamento.getId())) {
			throw new ConflitoException("equipamentoId",
					"Este equipamento não está disponível para retirada. Aguarde a devolução ou a avaliação.");
		}

		Emprestimo emprestimo = new Emprestimo();
		emprestimo.setSolicitacao(solicitacao);
		emprestimo.setEquipamento(equipamento);
		emprestimo.setOperadorRetirada(usuarioRepository.getReferenceById(operadorId));
		emprestimo.setDataRetirada(agora);
		emprestimo.setDataPrevistaDevolucao(solicitacao.getDataFimPrevista());
		try {
			emprestimo = emprestimoRepository.saveAndFlush(emprestimo);
		}
		catch (DataIntegrityViolationException ex) {
			// Última barreira: a restrição única do banco pegou um empréstimo em aberto concorrente.
			throw new ConflitoException("equipamentoId", "Este equipamento já tem um empréstimo em aberto.");
		}

		Situacao anterior = equipamento.getSituacao();
		equipamento.setSituacao(Situacao.EMPRESTADO);
		historicoService.registrarRetirada(equipamento, emprestimo, anterior, operadorId,
				"Retirado por %s.".formatted(solicitacao.getSolicitante().getUsuario().getNome()));
		return SolicitacaoResponse.de(solicitacao, emprestimo);
	}

}
