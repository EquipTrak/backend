package com.equiptrack.backend.emprestimo;

import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.equipamento.Situacao;
import com.equiptrack.backend.historico.HistoricoService;
import com.equiptrack.backend.solicitacao.SolicitacaoEmprestimo;
import com.equiptrack.backend.solicitacao.SolicitacaoRepository;
import com.equiptrack.backend.solicitacao.StatusSolicitacao;
import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.SolicitanteRepository;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Em desenvolvimento, dá ao NB-0002 (que o catálogo de demonstração cria EMPRESTADO) um empréstimo atrasado
 * de Beatriz Alves, para que a aba "Atrasados" e o bloqueio por atraso possam ser testados. Só age se o
 * NB-0002 estiver EMPRESTADO sem empréstimo em aberto, então não repete depois da devolução.
 */
@Component
@Profile("dev")
@Order(4) // Depois do catálogo (2) e dos solicitantes (3).
public class EmprestimosDevInitializer implements CommandLineRunner {

	static final String PATRIMONIO = "NB-0002";
	static final String EMAIL_SOLICITANTE = "beatriz.alves@equiptrack.local";
	static final String EMAIL_OPERADOR = "marina.souza@equiptrack.local";

	private final UsuarioRepository usuarioRepository;
	private final SolicitanteRepository solicitanteRepository;
	private final EquipamentoRepository equipamentoRepository;
	private final SolicitacaoRepository solicitacaoRepository;
	private final EmprestimoRepository emprestimoRepository;
	private final HistoricoService historicoService;
	private final Clock clock;

	public EmprestimosDevInitializer(UsuarioRepository usuarioRepository, SolicitanteRepository solicitanteRepository,
			EquipamentoRepository equipamentoRepository, SolicitacaoRepository solicitacaoRepository,
			EmprestimoRepository emprestimoRepository, HistoricoService historicoService, Clock clock) {
		this.usuarioRepository = usuarioRepository;
		this.solicitanteRepository = solicitanteRepository;
		this.equipamentoRepository = equipamentoRepository;
		this.solicitacaoRepository = solicitacaoRepository;
		this.emprestimoRepository = emprestimoRepository;
		this.historicoService = historicoService;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(String... args) {
		Usuario operadora = usuarioRepository.findByEmailIgnoreCase(EMAIL_OPERADOR).orElse(null);
		Solicitante beatriz = usuarioRepository.findByEmailIgnoreCase(EMAIL_SOLICITANTE)
			.flatMap(usuario -> solicitanteRepository.findByUsuarioId(usuario.getId()))
			.orElse(null);
		Equipamento notebook = equipamentoRepository.findByPatrimonioIgnoreCase(PATRIMONIO).orElse(null);
		if (operadora == null || beatriz == null || notebook == null || notebook.getSituacao() != Situacao.EMPRESTADO
				|| emprestimoRepository.existsByEquipamentoIdAndAtivoFlagTrue(notebook.getId())) {
			return;
		}

		Instant agora = clock.instant();
		Instant inicio = agora.minus(Duration.ofDays(4));
		Instant fim = agora.minus(Duration.ofDays(2));

		SolicitacaoEmprestimo solicitacao = new SolicitacaoEmprestimo();
		solicitacao.setSolicitante(beatriz);
		solicitacao.setEquipamento(notebook);
		solicitacao.setDataInicioPrevista(inicio);
		solicitacao.setDataFimPrevista(fim);
		solicitacao.setStatus(StatusSolicitacao.APROVADA);
		solicitacao.setOperadorDecisao(operadora);
		solicitacao.setDataDecisao(inicio);
		solicitacao = solicitacaoRepository.save(solicitacao);

		Emprestimo emprestimo = new Emprestimo();
		emprestimo.setSolicitacao(solicitacao);
		emprestimo.setEquipamento(notebook);
		emprestimo.setOperadorRetirada(operadora);
		emprestimo.setDataRetirada(inicio);
		emprestimo.setDataPrevistaDevolucao(fim);
		emprestimo = emprestimoRepository.save(emprestimo);

		historicoService.registrarRetirada(notebook, emprestimo, Situacao.DISPONIVEL, operadora.getId(),
				"Retirado por Beatriz Alves (dados de demonstração, já em atraso).");
	}

}
