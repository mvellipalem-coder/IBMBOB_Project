# PostgreSQL setup for local development

Database: **`ebookstore_db`**. Application user: **`ebookstore_user`**.
The schema is created by **Flyway** (`backend/src/main/resources/db/migration`); Hibernate only **validates** it.

## 1. Create the database (once)

Open **SQL Shell (psql)** or run `psql -U postgres` and execute (replace the password placeholder with your own strong password):

```sql
CREATE USER ebookstore_user WITH PASSWORD '<choose-a-strong-password>';
CREATE DATABASE ebookstore_db OWNER ebookstore_user ENCODING 'UTF8';
```

Optional hardening (only this user may connect):

```sql
REVOKE CONNECT ON DATABASE ebookstore_db FROM PUBLIC;
GRANT  CONNECT ON DATABASE ebookstore_db TO ebookstore_user;
```

Do not create any tables by hand. Flyway does it on first start.

## 2. Set environment variables (Windows PowerShell)

Current window only (recommended; forgotten when you close the window):

```powershell
$env:DB_URL      = "jdbc:postgresql://localhost:5432/ebookstore_db"
$env:DB_USERNAME = "ebookstore_user"
$env:DB_PASSWORD = "<the-password-you-chose>"
$env:JWT_SECRET  = "<random-string-of-at-least-32-characters>"
$env:SPRING_PROFILES_ACTIVE = "dev"
```

Type the password without leaving it in your command history:

```powershell
$secure = Read-Host "Database password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
```

Generate a random JWT secret:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

Keep them for every new window (stored unencrypted in your Windows user profile; use only on your own PC):

```powershell
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "<the-password-you-chose>", "User")
```

Check that a variable is set **without printing the secret**:

```powershell
if ($env:DB_PASSWORD) { "DB_PASSWORD is set" } else { "DB_PASSWORD is MISSING" }
```

Never put real values in `.env.example`, `application*.properties` or Git. Real `.env` files are git-ignored.

## 3. Profiles

| Profile | How to select | Purpose |
|---|---|---|
| *(default)* | nothing set | Production-safe base. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` are all required. |
| `dev` | `$env:SPRING_PROFILES_ACTIVE="dev"` | Local defaults for `DB_URL` and `DB_USERNAME`; DEBUG logging for our own code. |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Swagger UI off, JSON logs, quiet log level, `CORS_ALLOWED_ORIGIN` required, use `?sslmode=verify-full` in `DB_URL`. |

In every profile: `ddl-auto=validate`, Flyway `clean` disabled, server bound to `127.0.0.1`, no SQL logging.

## 4. Start the application

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"   # your JDK 21 folder
mvn spring-boot:run
```

Expected startup log (no secrets are printed):

```
Application started: name=ebookstore-backend, profiles=[dev], address=127.0.0.1, port=8080
Database configuration: url=jdbc:postgresql://localhost:5432/ebookstore_db, user=ebookstore_user, ddl-auto=validate
Database connection OK: product=PostgreSQL 18.x, database=ebookstore_db, flywaySchemaVersion=2
```

## 5. Verify database connectivity

1. **Application log:** the line `Database connection OK ... flywaySchemaVersion=2` means the app connected and Flyway finished.
2. **Health endpoint:** `Invoke-RestMethod http://localhost:8080/api/health` returns `status: UP`.
3. **psql, directly:**
   ```powershell
   psql -h localhost -U ebookstore_user -d ebookstore_db -c "SELECT current_database(), current_user;"
   psql -h localhost -U ebookstore_user -d ebookstore_db -c "\dt"
   psql -h localhost -U ebookstore_user -d ebookstore_db -c "SELECT version, description, success FROM flyway_schema_history;"
   psql -h localhost -U ebookstore_user -d ebookstore_db -c "SELECT count(*) FROM books;"
   ```
   psql asks for the password. Expect 14 tables plus `flyway_schema_history`, two successful migrations and 26 books.

## 6. Troubleshooting

| Message | Cause and fix |
|---|---|
| `Could not resolve placeholder 'DB_PASSWORD'` (or `DB_URL`, `JWT_SECRET`) | The variable is not set in this PowerShell window. Set it and run again. |
| `password authentication failed for user` | Wrong password, or the user does not exist. Re-run step 1. |
| `database "ebookstore_db" does not exist` | Run `CREATE DATABASE` in step 1. |
| `Connection to localhost:5432 refused` | PostgreSQL is not running. Start the *postgresql* Windows service. |
| `Schema-validation: missing table ...` | An entity does not match the migrations. Fix the entity or add a new migration. |
| `Migration checksum mismatch` | An applied migration file was edited. Restore it and add a new `V3__...sql` instead. |

## 7. Changing the schema later

Never edit an applied migration. Add a new file `V3__short_description.sql`, restart, and Flyway applies it once.
The reset of a local database is done by dropping and re-creating `ebookstore_db` (step 1), never by Hibernate.
