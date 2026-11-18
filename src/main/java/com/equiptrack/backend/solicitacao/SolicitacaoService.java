package com.equiptrack.backend.solicitacao;

import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.exception.CampoInvalidoException;
import com.equiptrack.backend.exception.ConflitoException;
import com.equiptrack.backend.exception.OperacaoNaoPermitidaException;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoRequest;
import com.equiptrack.backend.solicitacao.dto.SolicitacaoResponse;
import com.equiptrack.backend.solicitante.SituacaoMatricula;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.equiptrack.backend.solicitante.StatusSolicitante;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

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

	public SolicitacaoService(SolicitacaoRepository solicitacaoRepository,
			SolicitanteRepository solicitanteRepository, EquipamentoRepository equipamentoRepository) {
		this.solicitacaoRepository = solicitacaoRepository;
		this.solicitanteRepository = solicitanteRepository;
		this.equipamentoRepository = equipamentoRepository;
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

}
