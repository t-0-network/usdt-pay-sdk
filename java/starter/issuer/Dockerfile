# Build context is the repository root, because java/sdk/src/main/proto is a
# symlink into proto/ — a narrower context would not resolve it:
#
#   docker build -f java/starter/issuer/Dockerfile -t usdt-pay-issuer .
#
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /src
COPY . .

WORKDIR /src/java
RUN ./gradlew :starter:issuer:installDist --no-daemon

FROM eclipse-temurin:21-jre-noble

WORKDIR /app
COPY --from=build /src/java/starter/issuer/build/install/issuer/ ./

EXPOSE 8080

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
ENTRYPOINT ["./bin/issuer"]
