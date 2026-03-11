# nova-auth-client

A reusable Spring Boot auto-configured client library for **nova-auth-service**.  
Drop it into any microservice and get a ready-to-use `AuthServiceClient` bean — no boilerplate required.

---

## Table of Contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Installation](#installation)
  - [Option A — JFrog Artifactory OSS (recommended)](#option-a--jfrog-artifactory-oss-recommended)
  - [Option B — Local Maven Install](#option-b--local-maven-install)
- [Configuration](#configuration)
- [Usage](#usage)
  - [Validate an Inbound Token](#validate-an-inbound-token)
  - [Generate a Token](#generate-a-token)
  - [Fetch a User Profile](#fetch-a-user-profile)
  - [Validate + Profile in One Call](#validate--profile-in-one-call)
- [API Reference](#api-reference)
  - [AuthServiceClient Methods](#authserviceclient-methods)
  - [DTOs](#dtos)
- [How Auto-Configuration Works](#how-auto-configuration-works)
- [Publishing a New Version](#publishing-a-new-version)
- [Running Tests](#running-tests)

---

## Overview

```
┌──────────────────────┐        HTTP (WebFlux)        ┌──────────────────────┐
│   payment-service    │ ──────────────────────────► │   nova-auth-service  │
│   wallet-service     │   POST /api/auth/validate    │      (port 8081)     │
│   any-service        │   POST /api/auth/generate-token                     │
│                      │   GET  /api/auth/me           │                      │
└──────────────────────┘                              └──────────────────────┘
        │
        │  injects
        ▼
  AuthServiceClient  ◄─── auto-configured by nova-auth-client JAR
```

This library ships as a **plain JAR** (not a Spring Boot fat-jar). It registers itself via Spring Boot's auto-configuration mechanism, so the consuming service only needs to:

1. Add the dependency
2. Set one property (`nova.auth-client.url`)
3. Inject `AuthServiceClient`

---

## Project Structure

```
nova-auth-client/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/nova/auth/client/
    │   │   ├── AuthServiceClient.java              ← injectable bean
    │   │   ├── config/
    │   │   │   ├── AuthClientProperties.java       ← nova.auth-client.* bindings
    │   │   │   └── AuthClientAutoConfiguration.java
    │   │   └── dto/
    │   │       ├── TokenGenerationRequest.java
    │   │       ├── TokenGenerationResponse.java
    │   │       ├── TokenValidationRequest.java
    │   │       ├── TokenValidationResponse.java
    │   │       └── UserProfileResponse.java
    │   └── resources/META-INF/
    │       ├── spring/
    │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       └── additional-spring-configuration-metadata.json
    └── test/
        └── java/com/nova/auth/client/
            └── AuthServiceClientTest.java
```

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 17+ |
| Spring Boot | 3.2+ |
| Maven | 3.8+ |
| nova-auth-service | running & reachable |

---

## Installation

### Option A — JFrog Artifactory OSS (recommended)

This is the preferred approach for sharing the library across multiple services without copying JARs.

#### 1. Start Artifactory

```powershell
docker run -d --name artifactory -p 8081:8081 -p 8082:8082 `
  releases-docker.jfrog.io/jfrog/artifactory-oss:latest
```

Open **http://localhost:8082** and log in (`admin` / `password`). Create a **Local Maven** repository with key `nova-libs-release` and a **Virtual** repository with key `nova-libs` that wraps it.

#### 2. Configure credentials — `~/.m2/settings.xml`

```xml
<settings>
  <servers>
    <server>
      <id>artifactory-release</id>
      <username>admin</username>
      <password>your-password</password>
    </server>
    <server>
      <id>nova-artifactory</id>
      <username>admin</username>
      <password>your-password</password>
    </server>
  </servers>
</settings>
```

#### 3. Deploy the library

```powershell
cd C:\path\to\nova-auth-client
mvn clean deploy
```

#### 4. Add the repository in each consuming service's `pom.xml`

```xml
<repositories>
  <repository>
    <id>nova-artifactory</id>
    <name>Nova Artifactory Virtual</name>
    <url>http://localhost:8082/artifactory/nova-libs</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>false</enabled></snapshots>
  </repository>
</repositories>
```

#### 5. Add the dependency

```xml
<dependency>
  <groupId>com.nova</groupId>
  <artifactId>nova-auth-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

### Option B — Local Maven Install

Use this during development when Artifactory is not yet set up.

```powershell
cd C:\path\to\nova-auth-client
.\mvnw.cmd clean install -DskipTests
```

The JAR is installed to `~/.m2/repository/com/nova/nova-auth-client/1.0.0/`.  
No `<repositories>` block is needed in the consuming service — Maven finds it automatically.

---

## Configuration

Add the following to the consuming service's `application.properties` (or `application.yml`):

```properties
# Required — base URL of the running nova-auth-service
nova.auth-client.url=http://localhost:8081

# Optional — connection timeout in milliseconds (default: 3000)
nova.auth-client.connect-timeout-ms=3000

# Optional — read timeout in milliseconds (default: 5000)
nova.auth-client.read-timeout-ms=5000
```

```yaml
# application.yml equivalent
nova:
  auth-client:
    url: http://localhost:8081
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
```

> In Docker Compose, replace `localhost:8081` with the service name, e.g. `http://nova-auth-service:8081`.

---

## Usage

Inject `AuthServiceClient` into any Spring-managed bean — no `@Bean` definition needed.

```java
@Service
@RequiredArgsConstructor
public class MyService {

    private final AuthServiceClient authClient;
    // ...
}
```

### Validate an Inbound Token

Call this in every protected endpoint to verify the JWT sent by the client.

```java
@GetMapping("/protected")
public Mono<ResponseEntity<String>> protectedEndpoint(
        @RequestHeader("Authorization") String authHeader) {

    String token = authHeader.replace("Bearer ", "");

    return authClient.validateToken(token)
            .flatMap(validation -> {
                if (!validation.isValid()) {
                    return Mono.just(ResponseEntity.status(401).<String>build());
                }
                // validation.getUserId(), validation.getUsername() are now available
                return Mono.just(ResponseEntity.ok("Hello, " + validation.getUsername()));
            });
}
```

---

### Generate a Token

Use this when a service needs to obtain a JWT on behalf of a user (e.g. a BFF layer or internal service call).

```java
authClient.generateToken("john.doe", "secret123")
        .subscribe(response -> {
            String jwt      = response.getToken();
            Long   userId   = response.getUserId();
            Long   expiresIn = response.getExpiresIn(); // seconds
        });
```

---

### Fetch a User Profile

Retrieves the full user profile for an already-validated token.

```java
authClient.getUserProfile(token)
        .subscribe(profile -> {
            System.out.println(profile.getFirstName()); // e.g. "John"
            System.out.println(profile.getEmail());
            System.out.println(profile.getStatus());    // e.g. "ACTIVE"
        });
```

---

### Validate + Profile in One Call

Validates the token **and** fetches the profile in a single chain. Returns an empty `Mono` if the token is invalid — no exception thrown.

```java
@GetMapping("/wallet/balance")
public Mono<ResponseEntity<BalanceResponse>> getBalance(
        @RequestHeader("Authorization") String authHeader) {

    String token = authHeader.replace("Bearer ", "");

    return authClient.validateAndGetProfile(token)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
            .flatMap(profile -> walletService.getBalance(profile.getUserId()))
            .map(ResponseEntity::ok);
}
```

---

## API Reference

### AuthServiceClient Methods

| Method | Auth-Service Endpoint | Returns | Notes |
|--------|----------------------|---------|-------|
| `validateToken(String token)` | `POST /api/auth/validate` | `Mono<TokenValidationResponse>` | Always emits one item, never throws |
| `generateToken(String username, String password)` | `POST /api/auth/generate-token` | `Mono<TokenGenerationResponse>` | Empty if credentials invalid |
| `getUserProfile(String token)` | `GET /api/auth/me?token=` | `Mono<UserProfileResponse>` | Empty if token invalid |
| `validateAndGetProfile(String token)` | both above | `Mono<UserProfileResponse>` | Convenience method — empty if invalid |

---

### DTOs

#### `TokenValidationResponse`

| Field | Type | Description |
|-------|------|-------------|
| `valid` | `boolean` | Whether the token is valid and not expired |
| `userId` | `Long` | User ID embedded in the token |
| `username` | `String` | Username embedded in the token |
| `email` | `String` | User email |
| `reason` | `String` | Failure reason when `valid = false` |

#### `TokenGenerationResponse`

| Field | Type | Description |
|-------|------|-------------|
| `token` | `String` | The generated JWT |
| `username` | `String` | Authenticated username |
| `email` | `String` | User email |
| `userId` | `Long` | User ID |
| `expiresIn` | `Long` | Token lifetime in seconds |

#### `UserProfileResponse`

| Field | Type | Description |
|-------|------|-------------|
| `userId` | `Long` | User ID |
| `username` | `String` | Username |
| `email` | `String` | Email address |
| `firstName` | `String` | First name |
| `lastName` | `String` | Last name |
| `status` | `String` | Account status (e.g. `ACTIVE`) |

---

## How Auto-Configuration Works

Spring Boot reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` from the JAR classpath and registers `AuthClientAutoConfiguration`. This class:

1. Binds `AuthClientProperties` from `nova.auth-client.*`
2. Creates and registers the `AuthServiceClient` bean

No `@ComponentScan` or `@Import` annotation is needed in the consuming service.

---

## Publishing a New Version

1. Bump `<version>` in `pom.xml` (e.g. `1.0.0` → `1.1.0`)
2. Deploy to Artifactory:
   ```powershell
   mvn clean deploy
   ```
3. Update the `<version>` in each consumer's `pom.xml`
4. Run `mvn dependency:resolve` in the consumer to pull the new version

---

## Running Tests

The test suite uses **MockWebServer** to simulate nova-auth-service responses — no running service needed.

```powershell
cd C:\path\to\nova-auth-client
.\mvnw.cmd test
```

Tests cover:
- Successful token validation
- Invalid token handling (auth-service returns 401)
- Successful token generation
- Auth-service unreachable (network error fallback)

#   n o v a - a u t h - c l i e n t - l i b r a r y  
 