package com.equiptrack.backend.historico;

import com.equiptrack.backend.equipamento.Equipamento;
import com.equiptrack.backend.equipamento.EquipamentoRepository;
import com.equiptrack.backend.exception.RecursoNaoEncontradoException;
import com.equiptrack.backend.historico.dto.EventoHistoricoResponse;
import com.equiptrack.backend.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoricoService {

	private final HistoricoEquipamentoRepository historicoRepository;
	private final EquipamentoRepository equipamentoRepository;
	private final UsuarioRepository usuarioRepository;

	public HistoricoService(HistoricoEquipamentoRepository historicoRepository,
			EquipamentoRepository equipamentoRepository, UsuarioRepository usuarioRepository) {
		this.historicoRepository = historicoRepository;
		this.equipamentoRepository = equipamentoRepository;
		this.usuarioRepository = usuarioRepository;
	}

	/**
	 * Registra o cadastro do equipamento (RN11). Deve rodar na mesma transação do cadastro, para que não
	 * exista equipamento sem o evento inicial.
	 */
	@Transactional
	public void registrarCadastro(Equipamento equipamento, Long usuarioId) {
		HistoricoEquipamento evento = new HistoricoEquipamento();
		evento.setEquipamento(equipamento);
		evento.setUsuario(usuarioRepository.getReferenceById(usuarioId));
		evento.setTipo(TipoEvento.CADASTRO);
		evento.setSituacaoNova(equipamento.getSituacao());
		evento.setCondicaoNova(equipamento.getCondicao());
		evento.setObservacao("Situação inicial definida.");
		historicoRepository.save(evento);
	}

	@Transactional(readOnly = true)
	public List<EventoHistoricoResponse> listarPorPatrimonio(String patrimonio) {
		Equipamento equipamento = equipamentoRepository.findByPatrimonioIgnoreCase(patrimonio.trim())
			.orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento não encontrado."));
		return historicoRepository.buscarPorEquipamento(equipamento.getId()).stream()
			.map(EventoHistoricoResponse::de)
			.toList();
	}

}
