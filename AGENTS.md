# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Maven project using Java 21 and Spring Boot 4.1.1. Production code lives under `src/main/java/com/example/barbershop`; keep new classes below this base package so Spring component scanning finds them. Application configuration belongs in `src/main/resources/application.properties`. If the project gains server-rendered pages or web assets, place them in `src/main/resources/templates` and `src/main/resources/static`, respectively.

Tests mirror the production package structure under `src/test/java`. Maven-generated output is written to `target/` and must not be committed. IDE-specific files under `.idea/` are also ignored.

## Build, Test, and Development Commands

Use the included Maven wrapper so contributors use the repository's configured Maven version:

- `./mvnw clean package` (Windows: `.\mvnw.cmd clean package`) compiles, tests, and creates the executable JAR in `target/`.
- `./mvnw test` runs the test suite without packaging.
- `./mvnw spring-boot:run` starts the application locally with development-friendly classpath handling.
- `java -jar target/barber-shop-0.0.1-SNAPSHOT.jar` runs a packaged build.

## Coding Style & Naming Conventions

Use four-space indentation and standard Java formatting. Name classes in `UpperCamelCase`, methods and fields in `lowerCamelCase`, and constants in `UPPER_SNAKE_CASE`. Use descriptive Spring suffixes such as `AppointmentController`, `BookingService`, and `CustomerRepository`. Keep controllers focused on HTTP concerns, services on business rules, and repositories on persistence. Prefer constructor injection over field injection.

No formatter or static-analysis plugin is currently configured; keep imports organized and avoid unrelated formatting changes.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot's test support. Name test classes with a `Tests` suffix and test methods by observable behavior, for example `rejectsBookingOutsideBusinessHours`. Use unit tests for isolated business rules and `@SpringBootTest` only when full-context integration is necessary. There is no configured coverage threshold, but new behavior and bug fixes should include focused tests. Run `./mvnw test` before submitting changes.

## Commit & Pull Request Guidelines

The repository has no commit history yet, so no established message convention can be inferred. Use short, imperative subjects such as `Add appointment validation`, and keep each commit focused.

Pull requests should explain the motivation and implementation, list verification commands, and link relevant issues. Include API examples for endpoint changes and screenshots only when user-visible output changes. Call out configuration or database-schema changes explicitly; never commit secrets or machine-specific settings.

## Testing requirements

- For every new feature, class, service, controller, repository behavior, or bug fix, add or update tests.
- Prefer unit tests when the code can be tested in isolation.
- Add integration tests when Spring Boot context, HTTP endpoints, persistence, or multiple layers are involved.
- Do not consider a task complete until relevant tests are implemented and passing.
- Use JUnit 5.
- Use Mockito for isolated unit tests where appropriate.
- For controllers, prefer MockMvc tests when suitable.
- Keep tests readable and focused on behavior.
- Before finishing a task, run the relevant test suite and report the result.
- 