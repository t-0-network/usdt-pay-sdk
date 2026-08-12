# Build context is the repository root, because java/sdk/src/main/proto is a
# symlink into proto/ — a narrower context would not resolve it:
#
#   docker build -f java/starter/acquirer/Dockerfile -t usdt-pay-acquirer .
#
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /src
COPY . .

WORKDIR /src/java
RUN ./gradlew :starter:acquirer:installDist --no-daemon

FROM eclipse-temurin:21-jre-noble

WORKDIR /app
COPY --from=build /src/java/starter/acquirer/build/install/acquirer/ ./

EXPOSE 8080

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
ENTRYPOINT ["./bin/acquirer"]
