# Day 2 Demo — Customer Service with Spring Boot

A Spring Boot 4.1 (Java 21) REST service that manages the customers referenced by the Day 1 ticket files (`C-100`, `C-101`, ...). It is layered as **controller → service → repository** and demonstrates IoC, dependency injection, beans, externalized configuration, profiles and Actuator.

Day 1 stays dependency-free in the parent folder; this project lives in its own folder so the Day 1 `javac` commands keep working.

## Prerequisite

A JDK 21. Maven is **not** required: the included Maven Wrapper (`./mvnw`, `mvnw.cmd` on Windows) downloads Maven 3.9 on first use.

## Run

```sh
cd day2-customer-service
./mvnw spring-boot:run                                              # default profile: dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod              # prod profile
./mvnw spring-boot:run -Dspring-boot.run.arguments=--support.customer.max-customers=5
```

Or build a jar and run it:

```sh
./mvnw package
java -jar target/customer-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## Try the API

```sh
curl localhost:8080/api/customers
curl localhost:8080/api/customers/C-100
curl -i -X POST localhost:8080/api/customers -H 'Content-Type: application/json' \
     -d '{"name":"Eve Kim","email":"eve@example.com"}'
curl -X PUT localhost:8080/api/customers/C-104 -H 'Content-Type: application/json' \
     -d '{"name":"Eve Kim","email":"eve.kim@example.com","tier":"PREMIUM"}'
curl -i -X DELETE localhost:8080/api/customers/C-104
```

| Method | Path | Success | Errors |
| --- | --- | --- | --- |
| GET | `/api/customers` | 200 list | |
| GET | `/api/customers/{id}` | 200 | 404 |
| POST | `/api/customers` | 201 + `Location` | 400 validation, 409 duplicate email / limit reached |
| PUT | `/api/customers/{id}` | 200 | 400, 404, 409 |
| DELETE | `/api/customers/{id}` | 204 | 404 |

`tier` (`STANDARD` or `PREMIUM`) is optional: on create it defaults to `support.customer.default-tier`; on update it keeps the current tier. Errors use the RFC 9457 `ProblemDetail` JSON format.

## Try Actuator

```sh
curl localhost:8080/actuator                                   # list of exposed endpoints
curl localhost:8080/actuator/health                            # includes custom "customerStore"
curl localhost:8080/actuator/info                              # app, build, java, active profiles
curl localhost:8080/actuator/metrics/support.customers.count   # custom gauge
curl localhost:8080/actuator/beans                             # every bean in the IoC container (dev only)
curl localhost:8080/actuator/configprops                       # bound support.customer.* values (dev only)
curl localhost:8080/actuator/conditions                        # why auto-configurations did/didn't apply (dev only)
```

## Run tests

```sh
./mvnw test
```

- `CustomerServiceTest` — plain JUnit, no Spring: the service is built with `new` and given a fixed `Clock`. This is the payoff of constructor injection.
- `CustomerControllerTest` — `@WebMvcTest` slice with the service replaced by a `@MockitoBean`.
- `CustomerServiceApplicationTests` — full `@SpringBootTest` context, once with `dev` and once with `prod`, checking wiring, profile-specific beans/properties and Actuator exposure.

## Concepts demonstrated

| Concept | Where |
| --- | --- |
| **IoC / component scanning** | `@SpringBootApplication` scans `com.example.support.customer`; `@RestController`, `@Service`, `@Repository`, `@Component` classes become container-managed singletons. |
| **Constructor DI** | `CustomerController` ← `CustomerService` ← `CustomerRepository` + `CustomerProperties` + `Clock`. No `new`, no field `@Autowired`; single constructors need no annotation. |
| **Program to an interface** | The service depends on `CustomerRepository`; `InMemoryCustomerRepository` is the injected implementation and can later be swapped for JPA without touching the service. |
| **`@Bean` methods** | `CustomerConfig` registers a `java.time.Clock`, a class we can't annotate ourselves. |
| **Type-safe config** | `CustomerProperties` binds `support.customer.*` with validation (`@Min`, `@NotNull`) and defaults; override with env vars (`SUPPORT_CUSTOMER_MAX_CUSTOMERS=5`) or `--support.customer.max-customers=5`. |
| **Profiles** | `application-dev.yml` / `application-prod.yml` override properties; `DevDataLoader` is `@Profile("dev")` and only exists in dev. `dev` is the default when no profile is set. |
| **Actuator** | Custom `HealthIndicator` (`customerStore`, OUT_OF_SERVICE when full), `InfoContributor` (active profiles), Micrometer gauge (`support.customers.count`), build info, and per-profile endpoint exposure. |

| Setting | dev | prod |
| --- | --- | --- |
| `support.customer.max-customers` | 50 | 10000 |
| Sample customers C-100..C-103 | yes | no |
| Actuator endpoints | health, info, metrics, beans, env, configprops, conditions, mappings, loggers | health, info |
| Health details | always | never (plus Kubernetes liveness/readiness probes) |

This is an instructional starter. Data is in memory and lost on restart, and the Actuator endpoints are unsecured. A production service would add persistence, Spring Security in front of the API and Actuator, and a separate management port.
