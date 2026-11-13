package com.equiptrack.backend.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, String tipo, Instant expiraEm, UsuarioResponse usuario) {
}
