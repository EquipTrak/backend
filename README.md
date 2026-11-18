# EquipTrack Backend

API REST do EquipTrack em Java 21 e Spring Boot 4.

Não é preciso instalar o JDK 21 para compilar: o Gradle baixa essa versão se ela não estiver na máquina. Basta ter um JDK recente para rodar o próprio Gradle.

## Testes

```bash
./gradlew test
```

Os testes usam H2 em memória e não precisam de Docker nem de banco instalado.

## Executar

Com Docker, usando o PostgreSQL do `compose.yaml`:

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

## Front-end

O front (repositório `EquipTrak/frontend`) chama esta API pelo prefixo `/api`. Com o backend na porta 8080, rode `npm start` no front e acesse `http://localhost:4200`.

## Documentação

- [Autenticação](docs/autenticacao.md)
- [Integração com o front-end](docs/integracao-frontend.md)
- Swagger UI: `http://localhost:8080/swagger-ui.html`, com a aplicação rodando
