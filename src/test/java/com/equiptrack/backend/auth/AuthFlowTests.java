package com.equiptrack.backend.auth;

import com.equiptrack.backend.usuario.Perfil;
import com.equiptrack.backend.usuario.Usuario;
import com.equiptrack.backend.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

	private static final String EMAIL = "operador@equiptrack.local";
	private static final String SENHA = "senha-de-teste";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TentativasLogin tentativasLogin;

	private Usuario usuario;

	@BeforeEach
	void prepararUsuario() {
		tentativasLogin.limparTudo();
		sessaoRepository.deleteAll();
		usuarioRepository.deleteAll();
		usuario = new Usuario();
		usuario.setNome("Operador");
		usuario.setEmail(EMAIL);
		usuario.setSenhaHash(passwordEncoder.encode(SENHA));
		usuario.setPerfil(Perfil.OPERADOR);
		usuario = usuarioRepository.save(usuario);
	}

	@Test
	void loginValidoEmiteTokenEGuardaApenasOHash() throws Exception {
		String resposta = mockMvc.perform(login(EMAIL, SENHA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tipo").value("Bearer"))
			.andExpect(jsonPath("$.expiraEm").isString())
			.andExpect(jsonPath("$.usuario.email").value(EMAIL))
			.andExpect(jsonPath("$.usuario.perfil").value("OPERADOR"))
			.andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
			.andReturn().getResponse().getContentAsString();

		String token = JsonPath.read(resposta, "$.token");
		assertThat(sessaoRepository.findAll()).singleElement()
			.satisfies(sessao -> assertThat(sessao.getTokenHash()).hasSize(64).isNotEqualTo(token));
	}

	@Test
	void loginAceitaEmailComMaiusculas() throws Exception {
		mockMvc.perform(login("OPERADOR@equiptrack.local", SENHA)).andExpect(status().isOk());
	}

	@Test
	void senhaErradaEEmailInexistenteRespondemIgual() throws Exception {
		String mensagem = "E-mail ou senha inválidos.";
		mockMvc.perform(login(EMAIL, "senha-errada"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.mensagem").value(mensagem));
		mockMvc.perform(login("ninguem@equiptrack.local", SENHA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.mensagem").value(mensagem));
		assertThat(sessaoRepository.count()).isZero();
	}

	@Test
	void usuarioDesativadoNaoFazLogin() throws Exception {
		desativarUsuario();
		mockMvc.perform(login(EMAIL, SENHA))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void usuarioDesativadoComSenhaErradaNaoDescobreOStatus() throws Exception {
		desativarUsuario();
		mockMvc.perform(login(EMAIL, "senha-errada")).andExpect(status().isUnauthorized());
	}

	@Test
	void corpoInvalidoRetornaErrosPorCampo() throws Exception {
		mockMvc.perform(login("nao-e-email", ""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.mensagem").value("Dados inválidos."))
			.andExpect(jsonPath("$.campos.email").value("O e-mail é inválido."))
			.andExpect(jsonPath("$.campos.senha").value("A senha é obrigatória."));
	}

	@Test
	void jsonMalformadoRetorna400() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ruim"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.mensagem").value("O corpo da requisição é inválido."));
	}

	@Test
	void tipoDeConteudoInvalidoRetorna415() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.status").value(415));
	}

	@Test
	void metodoNaoSuportadoRetorna405() throws Exception {
		String token = fazerLogin();
		mockMvc.perform(get("/api/auth/login").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.status").value(405));
	}

	@Test
	void rotaInexistenteRetorna404ParaAutenticado() throws Exception {
		String token = fazerLogin();
		mockMvc.perform(get("/api/nao-existe").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void meRetornaOUsuarioDoToken() throws Exception {
		String token = fazerLogin();
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(usuario.getId()))
			.andExpect(jsonPath("$.perfil").value("OPERADOR"));
	}

	@Test
	void semTokenOuComTokenInvalidoRetorna401EmJson() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.mensagem").value("Autenticação necessária."));
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("token-falso")))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevogaApenasOTokenUsado() throws Exception {
		String primeiro = fazerLogin();
		String segundo = fazerLogin();
		assertThat(primeiro).isNotEqualTo(segundo);

		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(primeiro)))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(primeiro)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(primeiro)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(segundo)))
			.andExpect(status().isOk());
	}

	@Test
	void logoutTodasRevogaTodasAsSessoesDoUsuario() throws Exception {
		String primeiro = fazerLogin();
		String segundo = fazerLogin();

		mockMvc.perform(post("/api/auth/logout-todas").header(HttpHeaders.AUTHORIZATION, bearer(primeiro)))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(primeiro)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(segundo)))
			.andExpect(status().isUnauthorized());
		fazerLogin();
	}

	@Test
	void excessoDeFalhasBloqueiaInclusiveASenhaCorreta() throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(login(EMAIL, "senha-errada")).andExpect(status().isUnauthorized());
		}
		mockMvc.perform(login(EMAIL, SENHA))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andExpect(jsonPath("$.status").value(429));
		assertThat(sessaoRepository.count()).isZero();
	}

	@Test
	void bloqueioNaoDependeDeMaiusculasNoEmail() throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(login(i % 2 == 0 ? EMAIL : EMAIL.toUpperCase(), "senha-errada"))
				.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(login(EMAIL, SENHA)).andExpect(status().isTooManyRequests());
	}

	@Test
	void loginCorretoZeraAsFalhas() throws Exception {
		for (int i = 0; i < 4; i++) {
			mockMvc.perform(login(EMAIL, "senha-errada")).andExpect(status().isUnauthorized());
		}
		fazerLogin();
		for (int i = 0; i < 4; i++) {
			mockMvc.perform(login(EMAIL, "senha-errada")).andExpect(status().isUnauthorized());
		}
		fazerLogin();
	}

	@Test
	void senhaLongaComAcentosNaoCausaErroInterno() throws Exception {
		mockMvc.perform(login(EMAIL, "ã".repeat(72))).andExpect(status().isUnauthorized());
		mockMvc.perform(login(EMAIL, "a".repeat(73)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos.senha").exists());
	}

	@Test
	void tokenExpiradoRetorna401() throws Exception {
		String token = fazerLogin();
		expirarSessoes();
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void novoLoginRemoveSessoesExpiradasDoUsuario() throws Exception {
		fazerLogin();
		expirarSessoes();
		fazerLogin();
		assertThat(sessaoRepository.count()).isEqualTo(1);
	}

	@Test
	void desativarUsuarioDerrubaOTokenJaEmitido() throws Exception {
		String token = fazerLogin();
		desativarUsuario();
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void corsPermiteApenasOFrontendConfigurado() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header(HttpHeaders.ORIGIN, "http://localhost:4200")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
		mockMvc.perform(options("/api/auth/login")
				.header(HttpHeaders.ORIGIN, "http://site-malicioso.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden());
	}

	@Test
	void documentacaoOpenApiEhPublicaEListaOsEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
			.andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
			.andExpect(jsonPath("$.paths['/api/auth/me'].get").exists());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String senha) {
		return post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha));
	}

	private String fazerLogin() throws Exception {
		String resposta = mockMvc.perform(login(EMAIL, SENHA))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private void desativarUsuario() {
		usuario.setAtivo(false);
		usuarioRepository.save(usuario);
	}

	private void expirarSessoes() {
		sessaoRepository.findAll().forEach(sessao -> {
			ReflectionTestUtils.setField(sessao, "expiraEm", Instant.now().minusSeconds(60));
			sessaoRepository.save(sessao);
		});
	}

}
