# Imagem do backend do EquipTrack (Spring Boot, Java 21).
# Dois estágios: o primeiro compila o jar com o Gradle; o segundo só carrega o jar num JRE enxuto.

# ---- Estágio 1: compilar ----
FROM eclipse-temurin:21-jdk AS compilar
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
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuário sem privilégios: o processo não precisa ser root.
RUN useradd --system --create-home --shell /usr/sbin/nologin equiptrack
COPY --from=compilar /app/build/libs/app.jar app.jar
USER equiptrack

EXPOSE 8080
# A configuração (perfil, banco, chave dos desenvolvedores) vem de variáveis de ambiente do compose.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
