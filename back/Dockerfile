# Etapa 1: Compilacion de Spring Boot con Maven y Java 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copiar definicion de dependencias para aprovechar cache de capas
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copiar codigo fuente y compilar artefacto omitiendo tests
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen de ejecucion ligera
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Instalar curl para el healthcheck del contenedor
RUN apk add --no-cache curl

# Copiar el jar compilado desde la etapa de construccion
COPY --from=builder /build/target/*.jar app.jar

# Crear directorio de almacenamiento local para predios
RUN mkdir -p /app/storage/predios

EXPOSE 8080

# Configuracion de memoria de JVM adaptada a entornos de recursos limitados (AWS Academy)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

