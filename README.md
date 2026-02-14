# Notification Service
Service responsible for managing notifications, event processing, and message delivery.

## Quick start

Prerequisites:
- Java 21+ (JDK)
- Docker (for container runs)
- [faang-infra services](https://github.com/bormoley1983/faang-infra) running locally or accessible

Run locally:
```sh
./gradlew bootRun
```

Run tests:
```sh
./gradlew test --info
```

Build and run in Docker:
```sh
./gradlew build
docker build -t notification-service .
docker run -p 8083:8083 notification-service
```

## Configuration

Main config: [src/main/resources/application.yaml](src/main/resources/application.yaml)  
Test config: [src/test/resources/application-test.yaml](src/test/resources/application-test.yaml)

**Note:** Base code structure and architecture patterns are based on [FAANG School](https://github.com/faang-school) educational project.