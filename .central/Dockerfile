# Runtime image only — you must supply the shadow JAR in the Docker build context.
#
# Build the JAR locally, then build the image:
#   ./gradlew :openrune-central:shadowJar
#   cp openrune-central/build/libs/openrune-central-server.jar .
#   docker build -t openrune-central .
#
# Optional custom filename in context:
#   docker build --build-arg JAR_FILE=my-central.jar -t openrune-central .

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system openrune && useradd --system --gid openrune --home-dir /app --shell /usr/sbin/nologin openrune

ARG JAR_FILE=openrune-central-server.jar
COPY ${JAR_FILE} /app/openrune-central-server.jar
RUN chown openrune:openrune /app/openrune-central-server.jar

USER openrune
EXPOSE 8080/tcp
EXPOSE 9091/tcp

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/openrune-central-server.jar"]
