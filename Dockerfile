# Imagem do backend do EquipTrack (Spring Boot, Java 21).
# Dois estágios: o primeiro compila o jar com o Gradle; o segundo só carrega o jar num JRE enxuto.

# ---- Estágio 1: compilar ----
FROM eclipse-temurin:21-jdk-noble AS compilar
WORKDIR /app

# O wrapper e os arquivos do Gradle vêm antes do código, para o Docker reaproveitar o download
# das dependências enquanto elas não mudarem.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
# Os testes rodam à parte (./gradlew test); aqui só se gera o jar.
RUN ./gradlew --no-daemon bootJar -x test

# ---- Estágio 2: executar ----
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

# Usuário sem privilégios: o processo não precisa ser root.
RUN useradd --system --create-home --shell /usr/sbin/nologin equiptrack
COPY --from=compilar /app/build/libs/app.jar app.jar
USER equiptrack

EXPOSE 8080

# Saudável = a aplicação respondeu UP no /actuator/health, o que inclui a conexão com o banco.
# A primeira checagem espera o Flyway aplicar as migrations e o Spring subir.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

# A configuração (perfil, banco, chave dos desenvolvedores) vem de variáveis de ambiente do compose.
# MaxRAMPercentage: o limite de memória do contêiner define o heap (o padrão da JVM usa só 25%).
# A forma exec faz o java ser o processo 1: recebe o SIGTERM do "docker stop" e encerra de forma graciosa.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
