# Build context is the repository root, because java/sdk/src/main/proto is a
# symlink into proto/ — a narrower context would not resolve it:
#
#   docker build -f java/starter/lp/Dockerfile -t usdt-pay-lp .
#
FROM eclipse-temurin:17-jdk-noble AS build

WORKDIR /src
COPY . .

WORKDIR /src/java
RUN ./gradlew :starter:lp:installDist --no-daemon

FROM eclipse-temurin:17-jre-noble

WORKDIR /app
COPY --from=build /src/java/starter/lp/build/install/lp/ ./

EXPOSE 8080

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
ENTRYPOINT ["./bin/lp"]
