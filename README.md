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

Com um MySQL já instalado, sem Docker:

```bash
SPRING_PROFILES_ACTIVE=mysql DB_USUARIO=root DB_SENHA=sua-senha ./gradlew bootRun
```

Para criar o primeiro administrador em desenvolvimento, adicione o perfil `dev` e defina a senha:

```bash
SPRING_PROFILES_ACTIVE=mysql,dev EQUIPTRACK_ADMIN_SENHA=sua-senha ./gradlew bootRun
```

O administrador usa o e-mail `admin@equiptrack.local`, que pode ser alterado em `EQUIPTRACK_ADMIN_EMAIL`.

## Documentação

- [Autenticação](docs/autenticacao.md)
- Swagger UI: `http://localhost:8080/swagger-ui.html`, com a aplicação rodando
