# nova-auth-client Shared Library: Complete Guide

## What was created

```
C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-client\
│
├── pom.xml                                              ← plain-JAR build, no Spring Boot fat-jar
│
└── src/
    ├── main/
    │   ├── java/com/nova/auth/client/
    │   │   ├── AuthServiceClient.java                   ← the injectable bean
    │   │   ├── dto/
    │   │   │   ├── TokenValidationRequest.java
    │   │   │   ├── TokenValidationResponse.java
    │   │   │   └── UserProfileResponse.java
    │   │   └── config/
    │   │       ├── AuthClientProperties.java            ← nova.auth-client.* bindings
    │   │       └── AuthClientAutoConfiguration.java     ← Spring Boot auto-config
    │   └── resources/
    │       └── META-INF/
    │           ├── spring/
    │           │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← registers auto-config
    │           └── additional-spring-configuration-metadata.json  ← IDE property hints
    └── test/
        └── java/com/nova/auth/client/
            └── AuthServiceClientTest.java               ← 4 tests using MockWebServer
```

---

## Step 1 — Copy the Maven Wrapper

The `nova-auth-client` folder has no wrapper yet. Copy from the auth-service:

```powershell
Copy-Item "C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-service\mvnw" `
          "C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-client\mvnw"

Copy-Item "C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-service\mvnw.cmd" `
          "C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-client\mvnw.cmd"

Copy-Item "C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-service\.mvn" `
          "C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-client\.mvn" -Recurse
```

---

## Step 2 — Build & Install to local ~/.m2

```powershell
cd C:\Users\LENOVO\OneDrive\FINTECH\nova-auth-client
.\mvnw.cmd clean install -DskipTests
```

You should see:
```
[INFO] BUILD SUCCESS
[INFO] Installing nova-auth-client-1.0.0.jar to ...\.m2\repository\com\nova\nova-auth-client\1.0.0\
```

---

## Step 3 — Add the dependency in any consumer service

In the consumer's `pom.xml` (e.g., `nova-wallet-service`):

```xml
<dependency>
    <groupId>com.nova</groupId>
    <artifactId>nova-auth-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Step 4 — Configure the consumer's application.properties

```properties
# URL where nova-auth-service is running
nova.auth-client.url=http://localhost:8081

# Optional timeouts (defaults shown)
nova.auth-client.connect-timeout-ms=3000
nova.auth-client.read-timeout-ms=5000
```

---

## Step 5 — Inject and use AuthServiceClient

The bean is auto-registered — just inject it:

```java
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final AuthServiceClient authClient;

    @GetMapping("/wallet/balance")
    public Mono<ResponseEntity<BalanceResponse>> getBalance(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");

        return authClient.validateToken(token)
                .flatMap(validation -> {
                    if (!validation.isValid()) {
                        return Mono.just(ResponseEntity.status(401).<BalanceResponse>build());
                    }
                    Long userId = validation.getUserId();
                    return walletService.getBalance(userId)
                            .map(ResponseEntity::ok);
                });
    }
}
```

### Or use the convenience method (validate + profile in one call):

```java
return authClient.validateAndGetProfile(token)
        .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid token")))
        .flatMap(profile -> walletService.getBalance(profile.getUserId()));
```

---

## Available methods on AuthServiceClient

| Method | Calls | Returns |
|--------|-------|---------|
| `validateToken(token)` | `POST /api/auth/validate` | `Mono<TokenValidationResponse>` — always emits, never throws |
| `getUserProfile(token)` | `GET /api/auth/me?token=` | `Mono<UserProfileResponse>` — empty if invalid |
| `validateAndGetProfile(token)` | both above | `Mono<UserProfileResponse>` — empty if invalid |

---

## Releasing a new version

1. Bump the version in `nova-auth-client/pom.xml`
2. Run `.\mvnw.cmd clean install` (or `mvn deploy` to a Nexus/Artifactory registry)
3. Update the `<version>` in each consumer's `pom.xml`
