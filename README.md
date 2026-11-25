# EquipTrack

Sistema de empréstimo e rastreabilidade de equipamentos compartilhados (notebooks, projetores, câmeras, kits). O solicitante pede um equipamento por um período; o operador aprova, registra a retirada e a devolução; o sistema controla atrasos, bloqueios e o histórico de cada equipamento.

Este repositório é o **backend** (API REST) e também o dono do `compose.yaml` que sobe o sistema inteiro em contêineres. O front-end fica em outro repositório.

| Parte | Repositório | Tecnologia |
|---|---|---|
| Backend (este) | [`EquipTrak/backend`](https://github.com/EquipTrak/backend) | Java 21, Spring Boot 4, Spring Security, JPA/Hibernate, Flyway |
| Front-end | [`EquipTrak/frontend`](https://github.com/EquipTrak/frontend) | Angular 22, TypeScript, Tailwind |
| Banco | — | MySQL 8.4 (produção e Docker); H2 nos testes; PostgreSQL só no `bootRun` de desenvolvimento |

```
navegador ──► frontend (nginx :4200) ──/api──► backend (Spring :8080) ──► MySQL (:3306)
              só esta porta fica aberta         rede interna do compose      volume mysql-dados
```

## Avaliar o sistema em um comando

Para testar o EquipTrack sem configurar nada: só precisa do **Docker Desktop** (ou Docker Engine com o Compose v2) e do **Git**.

1. Baixe este repositório e entre na pasta:

   ```bash
   git clone https://github.com/EquipTrak/backend.git
   cd backend
   ```

2. Rode o script de início:
   - **Windows:** dê dois cliques em `iniciar.cmd` (ou, no PowerShell, `powershell -ExecutionPolicy Bypass -File .\iniciar.ps1`).
   - **Linux ou macOS:** `./iniciar.sh`

3. Quando aparecer **"EquipTrack no ar"**, abra **http://localhost:4200** (no Windows o navegador abre sozinho) e entre com um dos usuários abaixo.

**Senha de todos: `EquipTrack@2026`**

| Usuário | Perfil | O que dá para testar |
|---|---|---|
| `admin@equiptrack.local` | Administrador | Tudo o que o operador faz. Criado pela migration `V3`, existe em qualquer instalação |
| `marina.souza@equiptrack.local` | Operador | Cadastrar equipamento, aprovar ou rejeitar solicitações, registrar retirada e devolução, painel de indicadores |
| `rafael.lima@equiptrack.local` | Solicitante | Solicitar equipamento, acompanhar e cancelar as próprias solicitações |
| `beatriz.alves@equiptrack.local` | Solicitante | Começa com o `NB-0002` em atraso, para ver o bloqueio por atraso |

Os três últimos, os equipamentos de exemplo e o empréstimo atrasado são dados de demonstração, que o script liga no `.env` (`EQUIPTRACK_DEMO_SENHA`).

O script confere se o Docker está rodando (no Windows, abre o Docker Desktop), baixa o repositório do front em `../frontend`, cria o `.env` com senhas aleatórias para o banco e a chave de desenvolvedor e com os dados de demonstração ligados, constrói as imagens e espera os três serviços ficarem saudáveis. **Na primeira vez leva de 5 a 10 minutos**; depois, menos de um minuto. Pode rodar de novo quando quiser: o `.env` e os dados do banco são mantidos.

Para parar: `docker compose stop`. Para apagar tudo, inclusive o banco: `docker compose down -v`. Se algo der errado, veja [Solução de problemas](#solução-de-problemas).

## Início rápido com Docker

O passo a passo manual, para instalar com os seus próprios valores e sem dados de demonstração. É o caminho recomendado para uso real. Não precisa instalar Java, Node nem MySQL: só o Docker.

### 1. Pré-requisitos

- Docker Desktop (Windows ou macOS) ou Docker Engine (Linux), com o Compose v2.
- Git.
- As portas **4200** (front), **8080** (backend) e **3307** (MySQL) livres, ou outras definidas no `.env` (passo 3).

> Este procedimento foi validado em **Windows 11 com Docker Desktop 29**. Linux e macOS usam os mesmos arquivos, mas **não foram testados**.

Confira a instalação:

```bash
docker --version
docker compose version
```

### 2. Clonar os dois repositórios, lado a lado

O front é construído a partir de `../frontend`, então os dois ficam na mesma pasta:

```bash
mkdir equiptrack && cd equiptrack
git clone https://github.com/EquipTrak/backend.git
git clone https://github.com/EquipTrak/frontend.git
cd backend
```

### 3. Configurar o `.env`

```bash
cp .env.example .env
```

No PowerShell ou no CMD: `copy .env.example .env`.

Abra o `.env` e troque as três senhas e a chave por valores seus. O `.env` não vai para o Git. Todas as variáveis estão em [Variáveis de ambiente](#variáveis-de-ambiente).

### 4. Subir

```bash
docker compose up --build -d
```

Na primeira vez demora, porque baixa as imagens e compila o backend e o front. A ordem de partida é garantida por health checks: o backend só inicia com o MySQL saudável e o front só inicia com o backend saudável.

### 5. Verificar

```bash
docker compose ps
```

Os três serviços devem aparecer como `healthy`. Se algum não ficar, veja o log:

```bash
docker compose logs backend
```

O backend responde em `http://localhost:8080/actuator/health` (retorna `{"status":"UP",...}`). As migrations já criam as categorias de equipamento (Notebook, Projetor, Câmera, Adaptador, Kit) e o administrador `admin@equiptrack.local`, com a senha `EquipTrack@2026`.

### 6. Trocar a senha do administrador e criar usuários

A senha do administrador inicial está neste README, então **troque-a em uso real**. A troca e o cadastro de outros usuários são feitos pela API de gestão, com a chave `EQUIPTRACK_CHAVE_DESENVOLVEDOR` do seu `.env` (ainda não há tela para isso).

No PowerShell (no Git Bash, Linux ou macOS, use `curl` com os mesmos cabeçalhos):

```powershell
$h = @{ 'X-Chave-Desenvolvedor' = 'SUA-CHAVE' }
# Trocar a senha do administrador (o id dele aparece na listagem)
Invoke-RestMethod -Uri http://localhost:8080/api/usuarios -Headers $h
Invoke-RestMethod -Method Patch -Uri http://localhost:8080/api/usuarios/1 -Headers $h -ContentType 'application/json; charset=utf-8' -Body (@{ senha = 'uma-senha-forte' } | ConvertTo-Json)
# Cadastrar um operador
$corpo = @{ nome = 'Operador'; email = 'operador@equiptrack.local'; senha = 'outra-senha-forte'; perfil = 'OPERADOR' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/usuarios -Headers $h -ContentType 'application/json; charset=utf-8' -Body $corpo
```

Os perfis são `SOLICITANTE`, `OPERADOR` e `ADMINISTRADOR`. O solicitante exige também `"solicitante":{"tipo":"ALUNO","matricula":"..."}`. A senha tem de 8 a 72 caracteres. Detalhes em [Integração com o front-end](docs/integracao-frontend.md#gestão-de-usuários-pelos-desenvolvedores).

### 7. Acessar

Abra **http://localhost:4200** e entre com `admin@equiptrack.local` (ou com um usuário que você criou).

### 8. Parar, reiniciar e limpar

| Para | Comando |
|---|---|
| Parar, **mantendo os dados** | `docker compose down` |
| Subir de novo | `docker compose up -d` |
| Refazer o backend após mudar o código | `docker compose up -d --build backend` |
| Ver o log de um serviço | `docker compose logs -f backend` |
| ⚠️ Parar e **apagar o banco** | `docker compose down -v` |

> ⚠️ **`down -v` apaga o volume `mysql-dados` e, com ele, todos os dados.** Não há como desfazer; faça um [backup](#backup-e-restauração) antes.

## Variáveis de ambiente

Definidas no `.env` (modelo em [`.env.example`](.env.example)):

| Variável | Obrigatória | Padrão | Para que serve |
|---|---|---|---|
| `MYSQL_ROOT_PASSWORD` | sim | — | Senha do `root` do MySQL do contêiner |
| `DB_SENHA` | sim | — | Senha do usuário `equiptrack`, usado pelo backend |
| `EQUIPTRACK_CHAVE_DESENVOLVEDOR` | sim | — | Chave da gestão de usuários (`/api/usuarios`). **Mínimo de 16 caracteres**, senão o backend não sobe |
| `EQUIPTRACK_DEMO_SENHA` | não | — | Liga os dados de demonstração (usuários, equipamentos e um empréstimo atrasado), todos com esta senha. Não use em produção |
| `FUSO_HORARIO` | não | `America/Sao_Paulo` | Fuso que decide em que dia vence um empréstimo |
| `FRONTEND_PORT` | não | `4200` | Porta do front no seu computador |
| `BACKEND_PORT` | não | `8080` | Porta do backend (só em `127.0.0.1`) |
| `MYSQL_PORT` | não | `3307` | Porta do MySQL (só em `127.0.0.1`) |
| `FRONTEND_CONTEXT` | não | `../frontend` | Pasta do repositório do front |
| `SWAGGER_ATIVO` | não | `false` | `true` liga o Swagger UI em `/swagger-ui.html` (ver [API](#api)) |

Fora do compose, o backend também lê `DB_URL`, `DB_USUARIO`, `DB_SENHA`, `CORS_ORIGENS` (padrão `http://localhost:4200`; não é necessário com o front atrás do nginx, que mantém a mesma origem) e `FORWARD_HEADERS_STRATEGY` (veja [Rede e segurança](#rede-e-segurança)).

## Banco de dados

O MySQL guarda os dados no volume `mysql-dados`, que **sobrevive** a `docker compose down` e à recriação dos contêineres.

### Migrations (Flyway)

O schema é versionado em [`src/main/resources/db/migration`](src/main/resources/db/migration) e aplicado sozinho quando o backend sobe com o perfil `mysql` (o do Docker). O Hibernate só **valida** (`ddl-auto=validate`) se as entidades batem com o banco.

- `V1__schema_inicial.sql`: as tabelas.
- `V2__categorias_iniciais.sql`: as cinco categorias; cada uma só entra se ainda não existir.
- `V3__usuario_administrador.sql`: o administrador inicial, `admin@equiptrack.local` com a senha `EquipTrack@2026`; só entra se esse e-mail ainda não existir.

Um banco criado por versões anteriores (quando o Hibernate criava as tabelas) é reconhecido como a versão 1 e recebe só as migrations seguintes. Para mudar o schema, crie `V4__descricao.sql`: nunca edite uma migration já publicada.

```bash
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" equiptrack -e "select version, description, success from flyway_schema_history"'
```

### Conectar com um cliente (Workbench, DBeaver)

`127.0.0.1`, porta `3307` (ou `MYSQL_PORT`), usuário `equiptrack`, senha `DB_SENHA`, banco `equiptrack`.

### Backup e restauração

Backup (cria `backup.sql` na pasta atual):

```bash
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines equiptrack' > backup.sql
```

Restauração. ⚠️ Sobrescreve o conteúdo do banco `equiptrack`; para só conferir o backup, restaure em outro banco criado à parte:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" equiptrack' < backup.sql
```

### Atualização do MySQL

O compose usa `mysql:8.4`. Um volume criado pelo `mysql:8.0` é atualizado sozinho na primeira partida (testado de 8.0.46 para 8.4.13, com os dados preservados). Faça um backup antes, por segurança.

## Rede e segurança

- **Só o front (4200) fica aberto à rede.** O MySQL e o backend escutam apenas em `127.0.0.1`; entre si falam pela rede interna do compose.
- O navegador sempre chama `/api` no próprio front (mesma origem); o nginx repassa ao backend. Por isso não há CORS no uso normal.
- `FORWARD_HEADERS_STRATEGY=framework` (já definido no compose) faz o bloqueio de tentativas de login enxergar o IP de quem chamou, que o nginx envia no `X-Forwarded-For`. **Só ligue isso com o backend fora do alcance direto da rede**, senão qualquer um poderia forjar o cabeçalho. No Docker Desktop (Windows e macOS), o IP visto é o do gateway do Docker, não o do cliente.
- Backend e front rodam sem root; o backend com sistema de arquivos somente leitura e sem capabilities.
- Swagger e os endpoints do Actuator ficam fechados; só `/actuator/health` é público, sem detalhes.
- Não há HTTPS neste compose. Em produção, ponha um proxy com TLS à frente do front.

## Logs

```bash
docker compose logs -f backend      # um serviço, acompanhando
docker compose logs --tail 100      # os três, últimas 100 linhas
```

O backend registra um aviso quando a chave de desenvolvedor é recusada.

## Executar sem Docker

Os testes usam H2 em memória e não precisam de Docker nem de banco instalado:

```bash
./gradlew test
```

Não é preciso instalar o JDK 21: o Gradle baixa essa versão. Basta um JDK recente para rodar o próprio Gradle.

**Backend pelo Gradle, com PostgreSQL descartável em Docker.** O `./gradlew bootRun` sobe sozinho o `compose.dev-postgres.yaml` (porta aleatória, só em `127.0.0.1`):

```bash
./gradlew bootRun
```

**Com um MySQL já instalado**, pelo perfil `mysql`. A senha e a chave ficam em um arquivo local que não vai para o Git:

1. Copie `config/application-mysql.properties.example` para `config/application-mysql.properties` e preencha a senha do MySQL e a chave (mínimo de 16 caracteres).
2. Rode (o Spring Boot lê a pasta `config/` sozinho):

```bash
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
```

No PowerShell: `$env:SPRING_PROFILES_ACTIVE='mysql'; .\gradlew.bat bootRun`.

O banco `equiptrack` é criado se não existir e o Flyway aplica as migrations. Sem o perfil `dev`, ele começa só com o administrador da migration `V3` (`admin@equiptrack.local`, senha `EquipTrack@2026`).

**Dados de demonstração** (só em desenvolvimento): adicione o perfil `dev` e defina a senha deles:

```bash
SPRING_PROFILES_ACTIVE=mysql,dev EQUIPTRACK_DEV_SENHA=sua-senha ./gradlew bootRun
```

| E-mail | Perfil |
|---|---|
| `admin@equiptrack.local` | Administrador |
| `marina.souza@equiptrack.local` | Operador |
| `rafael.lima@equiptrack.local` | Solicitante |
| `beatriz.alves@equiptrack.local` | Solicitante |

Todos usam a mesma senha. Os dois solicitantes ganham o cadastro de solicitante (matrículas `DEMO-0001` e `DEMO-0002`). Beatriz Alves começa com o `NB-0002` em atraso, para testar a aba "Atrasados" e o bloqueio por atraso.

## Testes

```bash
./gradlew test
```

Cobrem autenticação, regras de empréstimo, atraso, histórico, painel, gestão de usuários, exposição operacional (Swagger/Actuator) e a concorrência na devolução. Usam H2 em memória.

## API

Os endpoints (resumo; o contrato completo está em [Integração com o front-end](docs/integracao-frontend.md) e [Autenticação](docs/autenticacao.md)):

| Método e rota | Quem acessa |
|---|---|
| `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/logout-todas`, `GET /api/auth/me` | login público; os demais, qualquer usuário logado |
| `GET /api/categorias`, `GET /api/equipamentos` | qualquer usuário logado |
| `POST /api/equipamentos`, `GET /api/equipamentos/{patrimonio}/historico` | operador e administrador |
| `POST /api/solicitacoes`, `GET /api/solicitacoes/minhas`, `POST /api/solicitacoes/{id}/cancelamento` | solicitante |
| `GET /api/solicitacoes`, `POST /api/solicitacoes/{id}/aprovacao`, `.../rejeicao`, `.../retirada` | operador e administrador |
| `POST /api/emprestimos/{id}/devolucao`, `GET /api/painel/indicadores` | operador e administrador |
| `/api/usuarios` | só com a chave de desenvolvedor (`X-Chave-Desenvolvedor`) |

**Swagger UI** fica desligado por padrão, porque descreve todas as rotas, inclusive as administrativas. Para ligar em um ambiente de teste, defina `SWAGGER_ATIVO=true` e abra `http://localhost:8080/swagger-ui.html`.

## Solução de problemas

| Sintoma | O que fazer |
|---|---|
| `port is already allocated` / porta em uso | Outra coisa usa 4200, 8080 ou 3307 (por exemplo, o front ou o backend rodando fora do Docker). Pare-a ou mude `FRONTEND_PORT`, `BACKEND_PORT` ou `MYSQL_PORT` no `.env` e suba de novo. |
| `Defina MYSQL_ROOT_PASSWORD no arquivo .env` | O `.env` não existe ou está sem essa variável. Refaça o passo 3. |
| O backend reinicia ou não fica `healthy` | `docker compose logs backend`. Causas comuns: chave com menos de 16 caracteres; senha do `.env` diferente da usada quando o volume foi criado (veja "Volume com dados antigos"). |
| O banco ainda não está pronto | O compose já espera o health check do MySQL. Se mesmo assim falhar, aguarde um minuto e rode `docker compose up -d` de novo. |
| O front abre, mas as chamadas falham (502) | O backend não está de pé: `docker compose ps` e `docker compose logs backend`. |
| Erro de migrations (`Validate failed`, `Schema-validation`) | O banco foi alterado fora do Flyway. Restaure um backup, ou use outro banco. Não apague o volume antes de guardar um backup. |
| Falha de build do front | Confira se `../frontend` existe (ou defina `FRONTEND_CONTEXT`) e se o Docker tem acesso à internet para baixar as dependências. |
| Permissão negada em volumes/arquivos | No Linux, rode o Docker com seu usuário no grupo `docker`. Os dados ficam em volume gerenciado pelo Docker, sem permissões a acertar. |
| Volume com dados antigos / senha trocada no `.env` | O MySQL só lê `MYSQL_*` ao **criar** o volume. Para trocar a senha de um banco já criado, altere-a dentro do MySQL (`ALTER USER`). Só apague o volume (`down -v`) se puder perder os dados e depois de um backup. |
| O front não alcança o backend (DNS entre contêineres) | Os dois precisam estar no mesmo compose; o front usa `API_URL=http://backend:8080`. Verifique com `docker compose ps` e use `docker compose up -d` (não suba o front isolado). |
| Login retorna "Acesso desativado" ou 403 | Usuário desativado, ou acesso por um endereço diferente do usado na configuração. Acesse pelo front (`http://localhost:4200`), não pelo backend direto. |

## Documentação

- [Autenticação](docs/autenticacao.md)
- [Integração com o front-end](docs/integracao-frontend.md)
- [Auditoria técnica e plano de correções](docs/auditoria.md)
