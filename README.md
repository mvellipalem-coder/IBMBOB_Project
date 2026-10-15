# EBookStore

E-commerce bookstore: browse, search, buy and manage book orders.
Stack: Spring Boot + Maven + PostgreSQL (backend), React + Vite (frontend), OpenAPI 3 / Swagger UI, JUnit 5 + Mockito.

See [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) for requirements, architecture and phases.
Current status: **Phase 0 (project setup)** complete.

## Prerequisites
- Java 21 (set `JAVA_HOME` to the JDK 21 folder)
- Maven 3.9+
- PostgreSQL (latest supported version)
- Node.js (current LTS) and npm
- Git

## 1. Database
```sql
CREATE USER ebookstore WITH PASSWORD '<choose-a-strong-password>';
CREATE DATABASE ebookstore OWNER ebookstore;
```

## 2. Run the backend
PowerShell:
```powershell
cd backend
$env:DB_PASSWORD = "<the-password-you-chose>"
mvn spring-boot:run
```
- Health check: http://localhost:8080/api/v1/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Other settings (optional): `DB_URL`, `DB_USERNAME`, `CORS_ALLOWED_ORIGIN` (see [.env.example](.env.example)).
Never commit real passwords or secrets.

## 3. Run the frontend
```powershell
cd frontend
npm install
npm run dev
```
Open http://localhost:5173. The home page shows the backend status.

## 4. Run the tests
```powershell
cd backend
mvn test
```

## Project layout
```
backend/    Spring Boot API
frontend/   React app
docs/       Plan and documentation
```
