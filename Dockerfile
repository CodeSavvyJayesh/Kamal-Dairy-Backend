# Two stages: build with the JDK, ship with the JRE.
#
# The single-stage version shipped Maven, the full JDK and the whole ~/.m2 cache
# to production - a few hundred megabytes of build tooling that a running
# container has no use for, and that is slow to push and pull on every deploy.
# Only the finished jar crosses into the second stage.

# --------------------------------------------------------------------- build
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Dependencies first, in their own layer. Application code changes on every
# commit; the dependency list almost never does, so Docker reuses this layer and
# a rebuild does not re-download the internet.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Fixed name, so the run stage does not depend on the version in pom.xml.
RUN cp target/*.jar app.jar

# ----------------------------------------------------------------------- run
FROM eclipse-temurin:21-jre

WORKDIR /app

# Never run as root. If anything ever escapes the JVM it lands as a user that
# owns nothing.
RUN groupadd --system kamal && useradd --system --gid kamal --home /app kamal
COPY --from=build --chown=kamal:kamal /build/app.jar app.jar
USER kamal

# Documentation only. Railway injects PORT and the app binds to it; see
# server.port in application-railway.properties.
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

# Shell form so $JAVA_OPTS expands, exec so the JVM becomes PID 1 and receives
# the platform's shutdown signal instead of being killed outright.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
