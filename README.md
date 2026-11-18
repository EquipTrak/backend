# EquipTrack Backend

API REST do EquipTrack em Java 21 e Spring Boot 4.

Não é preciso instalar o JDK 21 para compilar: o Gradle baixa essa versão se ela não estiver na máquina. Basta ter um JDK recente para rodar o próprio Gradle.

## Testes

```bash
./gradlew test
```

Os testes usam H2 em memória e não precisam de Docker nem de banco instalado.

## Executar

Só o banco em Docker, com o backend rodando pelo Gradle: o `./gradlew bootRun` sobe sozinho o PostgreSQL do `compose.yaml`. Para rodar **tudo** em contêineres, com MySQL, veja [Rodar tudo com Docker](#rodar-tudo-com-docker-mysql--backend--front).

```bash
./gradlew bootRun
```

Com um MySQL já instalado, sem Docker, usando o perfil `mysql`. A senha do banco e a chave dos desenvolvedores ficam em um arquivo local, que não vai para o Git:

1. Copie `config/application-mysql.properties.example` para `config/application-mysql.properties` e preencha a senha do MySQL e a chave.
2. Rode, sem variáveis de ambiente (o Spring Boot lê a pasta `config/` sozinho):

```bash
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
```

No PowerShell: `$env:SPRING_PROFILES_ACTIVE='mysql'; .\gradlew.bat bootRun`.

O banco `equiptrack` é criado se não existir, e o Hibernate cria as tabelas. Sem o perfil `dev`, ele começa **sem usuários, equipamentos nem categorias**, e os dados ficam guardados entre as execuções. O primeiro usuário é criado pela API de gestão, com a chave de desenvolvedor (ver [Integração com o front-end](docs/integracao-frontend.md#gestão-de-usuários-pelos-desenvolvedores)):

```bash
curl -X POST http://localhost:8080/api/usuarios -H "X-Chave-Desenvolvedor: SUA-CHAVE" -H "Content-Type: application/json" -d '{"nome":"Administrador","email":"admin@equiptrack.local","senha":"uma-senha-forte","perfil":"ADMINISTRADOR"}'
```

Também dá para passar as configurações por variável de ambiente: `DB_URL`, `DB_USUARIO`, `DB_SENHA` e `EQUIPTRACK_CHAVE_DESENVOLVEDOR`.

Para criar usuários de demonstração em desenvolvimento, adicione o perfil `dev` e defina a senha deles:

```bash
SPRING_PROFILES_ACTIVE=mysql,dev EQUIPTRACK_DEV_SENHA=sua-senha ./gradlew bootRun
```

Todos usam a mesma senha:

| E-mail | Perfil |
|---|---|
| `admin@equiptrack.local` | Administrador |
| `marina.souza@equiptrack.local` | Operador |
| `rafael.lima@equiptrack.local` | Solicitante |
| `beatriz.alves@equiptrack.local` | Solicitante |

Os dois solicitantes também ganham o cadastro de solicitante (matrículas `DEMO-0001` e `DEMO-0002`), necessário para criar solicitações. Beatriz Alves começa com o `NB-0002` em atraso, para testar a aba "Atrasados" e o bloqueio por atraso.

## Rodar tudo com Docker (MySQL + backend + front)

O `compose.mysql.yaml` sobe os três serviços juntos: o **MySQL 8**, o **backend** e o **front**. Não é preciso instalar Java, Node nem MySQL: só o Docker.

Requisitos:

- Docker Desktop (ou Docker Engine) com o Compose v2, o comando `docker compose`.
- Os repositórios `backend` e `frontend` **lado a lado**, na mesma pasta (o `instalar-equiptrack.ps1` já deixa assim), pois o front é construído a partir de `../frontend`.
- As portas **4200** (front), **8080** (backend) e **3307** (MySQL no seu computador) livres. Se você roda o backend ou o front fora do Docker, pare-os antes.

Passo a passo, na pasta do backend:

1. Crie o arquivo `.env` a partir do modelo e preencha as duas senhas e a chave. O `.env` não vai para o Git:

   ```bash
   cp .env.example .env
   ```

   No CMD ou no PowerShell: `copy .env.example .env`.

2. Suba tudo. Na primeira vez demora, porque baixa as imagens e compila o backend e o front:

   ```bash
   docker compose -f compose.mysql.yaml up --build
   ```

   O backend só inicia depois que o MySQL ficar saudável. Está pronto quando o log do backend mostra `Started BackendApplication`.

3. Acesse `http://localhost:4200`. O banco começa **vazio**, sem usuários, equipamentos nem categorias, então ainda não dá para entrar.

4. Crie o primeiro usuário pela API de gestão, com a chave do `.env` (`EQUIPTRACK_CHAVE_DESENVOLVEDOR`). No Git Bash:

   ```bash
   curl -X POST http://localhost:8080/api/usuarios -H "X-Chave-Desenvolvedor: SUA-CHAVE" -H "Content-Type: application/json" -d '{"nome":"Administrador","email":"admin@equiptrack.local","senha":"uma-senha-forte","perfil":"ADMINISTRADOR"}'
   ```

   O PowerShell e o CMD quebram as aspas do JSON no `curl`; use o Git Bash ou o `Invoke-RestMethod` do PowerShell. Os perfis são `SOLICITANTE`, `OPERADOR` e `ADMINISTRADOR`, e o solicitante exige também `"solicitante":{"tipo":"ALUNO","matricula":"..."}`. Os detalhes estão em [Integração com o front-end](docs/integracao-frontend.md#gestão-de-usuários-pelos-desenvolvedores).

5. Crie ao menos uma categoria de equipamento, sem a qual não dá para cadastrar equipamentos (ainda não há API para categorias). Abra o MySQL do contêiner, digite a senha do root (`MYSQL_ROOT_PASSWORD`, do `.env`) e rode o SQL:

   ```bash
   docker compose -f compose.mysql.yaml exec mysql mysql -u root -p --default-character-set=utf8mb4 equiptrack
   ```

   ```sql
   INSERT INTO categoria_equipamento (nome, descricao, ativa) VALUES
   	('Notebook',  'Notebooks para aulas e atividades em campo', 1),
   	('Projetor',  'Projetores multimídia', 1),
   	('Câmera',    'Câmeras fotográficas e de vídeo', 1),
   	('Adaptador', 'Adaptadores e cabos (HDMI, VGA, USB-C)', 1),
   	('Kit',       'Kits de iluminação, tripés e acessórios', 1)
   ON DUPLICATE KEY UPDATE descricao = VALUES(descricao), ativa = 1;
   ```

Comandos úteis, sempre com `-f compose.mysql.yaml`:

| Para | Comando |
|---|---|
| Subir em segundo plano | `docker compose -f compose.mysql.yaml up -d --build` |
| Ver o log de um serviço | `docker compose -f compose.mysql.yaml logs -f backend` |
| Parar, mantendo os dados | `docker compose -f compose.mysql.yaml down` |
| Parar e **apagar o banco** | `docker compose -f compose.mysql.yaml down -v` |
| Refazer o backend após mudar o código | `docker compose -f compose.mysql.yaml up -d --build backend` |

Bom saber:

- Os dados do MySQL ficam no volume `mysql-dados` e sobrevivem ao `down`. Só o `down -v` os apaga.
- Para olhar o banco por fora do contêiner (MySQL Workbench, DBeaver), conecte em `127.0.0.1`, porta **3307**, usuário `equiptrack` e a senha `DB_SENHA` do `.env`.
- O front roda em modo de desenvolvimento (`ng serve`) e repassa `/api` para o backend pela rede do compose, então o navegador não precisa de CORS. Serve para uso interno e testes; não é um build otimizado de produção.
- O arquivo não se chama `compose.yaml` de propósito: o `compose.yaml` da pasta é o do PostgreSQL, que o Spring Boot sobe sozinho com `./gradlew bootRun`.
- Os testes (`./gradlew test`) continuam usando H2 em memória e não dependem do Docker.

## Front-end

O front (repositório `EquipTrak/frontend`) chama esta API pelo prefixo `/api`. Com o backend na porta 8080, rode `npm start` no front e acesse `http://localhost:4200`.

## Documentação

- [Autenticação](docs/autenticacao.md)
- [Integração com o front-end](docs/integracao-frontend.md)
- Swagger UI: `http://localhost:8080/swagger-ui.html`, com a aplicação rodando
