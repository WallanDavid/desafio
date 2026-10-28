# ==========================================
# Estágio 1: Build da aplicação (Builder)
# ==========================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Copia arquivos de dependência do Maven Wrapper primeiro para cache de camadas
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copia o código fonte e realiza o empacotamento com testes
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

# Extrai os layers do Spring Boot para inicialização rápida e otimização de imagem
RUN java -Djarmode=layertools -jar target/*.jar extract

# ==========================================
# Estágio 2: Imagem final enxuta (Runtime)
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Instala curl para suporte ao HEALTHCHECK nativo do Docker
RUN apk add --no-cache curl tzdata

# Configura timezone padrão para UTC
ENV TZ=UTC

# Cria usuário e grupo não-privilegiados para segurança em conformidade com CIS Docker Benchmark
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copia os layers extraídos pelo layertools do Spring Boot
COPY --from=builder /workspace/dependencies/ ./
COPY --from=builder /workspace/spring-boot-loader/ ./
COPY --from=builder /workspace/snapshot-dependencies/ ./
COPY --from=builder /workspace/application/ ./

# Exposição da porta da aplicação
EXPOSE 8080

# Configurações otimizadas para execução da JVM em contêineres e desligamento gracioso
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

# Healthcheck usando endpoint nativo de liveness do Spring Boot Actuator
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
