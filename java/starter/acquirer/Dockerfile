# Build context is this project directory:
#
#   docker build -t usdt-pay-acquirer .
#
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /src
COPY . .

RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre-noble

WORKDIR /app
COPY --from=build /src/build/install/acquirer/ ./

EXPOSE 8080

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
ENTRYPOINT ["./bin/acquirer"]
