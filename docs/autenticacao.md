# Autenticação

A API usa um token opaco enviado no cabeçalho `Authorization: Bearer <token>`.

- O token é aleatório (256 bits) e só é devolvido no login. O banco guarda apenas o hash SHA-256.
- Validade padrão de 8 horas, configurável em `equiptrack.auth.token-validade`.
- Logout, expiração e desativação do usuário invalidam o token na requisição seguinte.
- Com a aplicação rodando, a documentação interativa fica em `/swagger-ui.html`.

## Endpoints

### `POST /api/auth/login` (público)

```json
{ "email": "operador@equiptrack.local", "senha": "minha-senha" }
```

Resposta `200`:

```json
{
  "token": "q1w2e3...",
  "tipo": "Bearer",
  "expiraEm": "2026-11-05T05:30:00Z",
  "usuario": { "id": 1, "nome": "Operador", "email": "operador@equiptrack.local", "perfil": "OPERADOR" }
}
```

| Status | Quando |
|---|---|
| `400` | E-mail ou senha ausentes ou inválidos, ou JSON malformado |
| `401` | E-mail ou senha incorretos (mesma resposta nos dois casos) |
| `403` | Senha correta, mas usuário desativado |
| `429` | 5 falhas seguidas para o mesmo e-mail e IP; o cabeçalho `Retry-After` indica os segundos de espera |

O contador de falhas fica em memória, zera após 15 minutos, com um login correto ou ao reiniciar a aplicação. Atrás de um proxy reverso, todas as requisições chegam com o mesmo IP, então o limite passa a valer, na prática, por e-mail.

### `POST /api/auth/logout` (autenticado)

Revoga apenas o token usado na requisição. Sem corpo. Resposta `204`, ou `401` se o token já não for válido.

### `POST /api/auth/logout-todas` (autenticado)

Revoga todas as sessões do usuário, em qualquer dispositivo. Sem corpo. Resposta `204`, ou `401`.

### `GET /api/auth/me` (autenticado)

Resposta `200` com `{ "id", "nome", "email", "perfil" }`, ou `401`. O frontend pode usar para restaurar a sessão ao recarregar a página.

## Formato dos erros

Todos os erros da API seguem este formato. `campos` só aparece em erros de validação.

```json
{
  "timestamp": "2026-11-04T21:30:00Z",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Dados inválidos.",
  "caminho": "/api/auth/login",
  "campos": { "email": "O e-mail é inválido." }
}
```

## Banco de dados

A tabela `usuario` segue o DER. A revogação exige guardar os tokens, então esta tabela é um **acréscimo ao DER**:

```sql
CREATE TABLE sessao_usuario (
    id_sessao   BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario  BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    criada_em   DATETIME(6) NOT NULL,
    expira_em   DATETIME(6) NOT NULL,
    revogada_em DATETIME(6) NULL,
    CONSTRAINT uk_sessao_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_sessao_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB;
```

## Protegendo novos endpoints

Toda rota exige autenticação por padrão. Para ler o usuário logado em um controller:

```java
public Algo metodo(@AuthenticationPrincipal UsuarioAutenticado usuario) { ... }
```

As autoridades seguem o perfil: `ROLE_SOLICITANTE`, `ROLE_OPERADOR`, `ROLE_ADMINISTRADOR` e `ROLE_ENCARREGADO`.

## Configuração

| Propriedade ou variável | Padrão | Uso |
|---|---|---|
| `equiptrack.auth.token-validade` | `8h` | Validade do token |
| `equiptrack.auth.login.max-tentativas` | `5` | Falhas de login antes do bloqueio |
| `equiptrack.auth.login.bloqueio` | `15m` | Janela de contagem e duração do bloqueio |
| `CORS_ORIGENS` | `http://localhost:4200` | Origens do frontend, separadas por vírgula |
| `EQUIPTRACK_ADMIN_SENHA` | vazio | Só no perfil `dev`: cria o primeiro administrador |
| `EQUIPTRACK_ADMIN_EMAIL` | `admin@equiptrack.local` | Só no perfil `dev`: e-mail desse administrador |

## Testes

```bash
./gradlew test
```

Os testes usam H2 em memória, sem Docker ou banco instalado.
