package com.equiptrack.backend.security;

import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;

public record UsuarioAutenticado(Long id, Long sessaoId, String nome, String email, Perfil perfil) {

	public static UsuarioAutenticado de(Long sessaoId, Usuario usuario) {
		return new UsuarioAutenticado(usuario.getId(), sessaoId, usuario.getNome(), usuario.getEmail(),
				usuario.getPerfil());
	}

}
