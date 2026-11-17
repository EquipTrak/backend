package com.equiptrack.backend.usuario.dto;

import com.equiptrack.backend.solicitante.Solicitante;
import com.equiptrack.backend.solicitante.TipoSolicitante;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;

import java.time.LocalDateTime;

/** Usuário como os desenvolvedores o veem. A senha nunca sai da API, nem como hash. */
public record UsuarioDetalheResponse(Long id, String nome, String email, Perfil perfil, boolean ativo,
		LocalDateTime criadoEm, DadosSolicitante solicitante) {

	public record DadosSolicitante(TipoSolicitante tipo, String matricula, String telefone) {
	}

	public static UsuarioDetalheResponse de(Usuario usuario, Solicitante solicitante) {
		DadosSolicitante dados = solicitante == null ? null
				: new DadosSolicitante(solicitante.getTipo(), solicitante.getMatricula(), solicitante.getTelefone());
		return new UsuarioDetalheResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(),
				usuario.isAtivo(), usuario.getCriadoEm(), dados);
	}

}
