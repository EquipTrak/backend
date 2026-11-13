package com.equiptrack.backend.auth.dto;

import com.equiptrack.backend.security.UsuarioAutenticado;
import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;

public record UsuarioResponse(Long id, String nome, String email, Perfil perfil) {

	public static UsuarioResponse de(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
	}

	public static UsuarioResponse de(UsuarioAutenticado usuario) {
		return new UsuarioResponse(usuario.id(), usuario.nome(), usuario.email(), usuario.perfil());
	}

}
