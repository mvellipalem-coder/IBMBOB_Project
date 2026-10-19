# EBookStore

E-commerce bookstore: browse, search, buy and manage book orders.
Stack: Java 21, Spring Boot 4.1 + Maven + PostgreSQL (backend), React + Vite (frontend), OpenAPI 3 / Swagger UI, JUnit 5 + Mockito.

- Plan: [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md)
- Frontend integration, token strategy and missing backend endpoints: [docs/FRONTEND_INTEGRATION.md](docs/FRONTEND_INTEGRATION.md)
- Wireframe requirements mapped to pages and endpoints: [docs/WIREFRAME_TRACEABILITY.md](docs/WIREFRAME_TRACEABILITY.md)
- Database design: [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md)
- Authentication and authorization (samples, Swagger UI steps): [docs/AUTHENTICATION.md](docs/AUTHENTICATION.md)
- API contract: [backend/src/main/resources/openapi.yaml](backend/src/main/resources/openapi.yaml)

Current status: the backend API (authentication, catalogue, cart, addresses, checkout, payments, gift points,
orders, recommendations) and the React frontend that uses it are implemented. Admin features are not yet
(see [docs/FRONTEND_INTEGRATION.md](docs/FRONTEND_INTEGRATION.md)).

## Prerequisites
- Java 21 (set `JAVA_HOME` to the JDK 21 folder)
- Maven 3.9+
- PostgreSQL (latest supported version)
- Node.js (current LTS) and npm (frontend only)
- Git

## 1. Database
```sql
CREATE USER ebookstore_user WITH PASSWORD '<choose-a-strong-password>';
CREATE DATABASE ebookstore_db OWNER ebookstore_user ENCODING 'UTF8';
```
Flyway creates the tables and sample books automatically on first start.
Full guide (environment variables, profiles, verification, troubleshooting): [docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md).

## 2. Run the backend
PowerShell:
```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_PASSWORD = "<the-password-you-chose>"
$env:JWT_SECRET  = "<random-string-of-at-least-32-characters>"
mvn spring-boot:run
```
The `dev` profile defaults `DB_URL` to `jdbc:postgresql://localhost:5432/ebookstore_db` and `DB_USERNAME` to `ebookstore_user`.
Without a profile, `DB_URL` and `DB_USERNAME` must be set too. The server listens on 127.0.0.1 only.
- Health check: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI contract: http://localhost:8080/openapi.yaml

`DB_PASSWORD` and `JWT_SECRET` are required; the app refuses to start without them.
Optional settings (`SERVER_PORT`, `JWT_EXPIRATION_SECONDS`, `CORS_ALLOWED_ORIGIN`)
are listed in [.env.example](.env.example). Never commit real passwords or secrets.

## 3. Run the frontend
```powershell
cd frontend
npm install
npm run dev
```
Open http://localhost:5173. The backend must be running. The API address comes from `VITE_API_BASE_URL`
(see [frontend/.env.example](frontend/.env.example)); it defaults to `http://localhost:8080/api`.

## 4. Run the tests
```powershell
cd backend
mvn test

cd ../frontend
npm test
```
Backend tests start an embedded PostgreSQL automatically, so no database setup is needed for them.
Frontend tests run against a fake `fetch` and need no backend.

## Backend packages (`com.example.ebookstore`)
| Package | Responsibility |
|---|---|
| `config` | Spring configuration: security, OpenAPI/Swagger, typed settings |
| `controller` | HTTP endpoints; use DTOs only |
| `dto` | Request/response objects that match the OpenAPI contract |
| `entity` | JPA classes mapped to database tables |
| `exception` | Error codes and the central `@RestControllerAdvice` |
| `mapper` | Entity <-> DTO conversion |
| `repository` | Spring Data JPA interfaces |
| `security` | JWT and authentication helpers |
| `service` / `service.impl` | Business rules and transactions |

## Project layout
```
backend/    Spring Boot API
frontend/   React app
docs/       Plan and documentation
```
