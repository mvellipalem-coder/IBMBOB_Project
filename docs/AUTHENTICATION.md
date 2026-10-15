# Authentication and authorization

How users register, log in and are kept apart from each other. The behaviour follows the approved
[`openapi.yaml`](../backend/src/main/resources/openapi.yaml) (`/api/auth/register`, `/api/auth/login`, `/api/users/me`).

## How it works

```
Browser / API client                         Spring Boot
  POST /api/auth/register  ───────────────►  validate → lower-case e-mail → BCrypt hash → save (role = CUSTOMER)
  POST /api/auth/login     ───────────────►  check BCrypt hash → sign JWT (HS256, 30 min)
  GET  /api/users/me
  Authorization: Bearer <token> ──────────►  verify signature, expiry, issuer
                                             → load the user from the database (must exist and be enabled)
                                             → principal = AuthenticatedUser(id, role)
```

| Topic | Rule |
|---|---|
| Passwords | 8 to 72 UTF-8 bytes, at least one lower-case letter, one upper-case letter and one digit. Stored only as a BCrypt hash. Never returned or logged. |
| E-mail | Valid format, at most 254 characters, stored in lower case, unique ignoring case (409 `EMAIL_ALREADY_REGISTERED`; the database unique index also catches two simultaneous sign-ups). |
| Role | Always `CUSTOMER` on registration. A `role` field in the body is rejected with 400. An admin can only be created directly in the database. |
| Login errors | Wrong password, unknown e-mail and disabled account all return the same 401 `INVALID_CREDENTIALS`. |
| Token | JWT signed with HS256 using `JWT_SECRET`. Contains only `sub` (user id), `role`, `iss`, `iat`, `exp`, `jti`. Valid for `JWT_EXPIRATION_SECONDS` (default 1800). |
| Every request | The user is reloaded from the database. A deleted or disabled user is rejected at once, and the role always comes from the database, not from the token. |
| 401 | No token, malformed, expired, wrongly signed or `alg: none` token. Standard JSON error body. |
| 403 | Logged in but the role is not allowed (`@PreAuthorize`). Standard JSON error body with code `FORBIDDEN`. |
| CORS | Only `CORS_ALLOWED_ORIGIN` (default `http://localhost:5173`, the React dev server) may call `/api/**` from a browser. |
| Public URLs | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/books/**`, `/api/categories`, `/api/publishers`, `/api/health`, `/openapi.yaml`, Swagger UI. Everything else needs a token. |

### Ownership: a user only sees their own data

- The current user comes **only** from the verified token, through `@AuthenticationPrincipal AuthenticatedUser`.
- No endpoint takes a user id from the client. There is no `/api/users/{id}`; `/api/users/me` is the only profile endpoint.
- Every future query for carts, addresses, orders, payments and gift points must filter by `AuthenticatedUser.id()`.
  A record that belongs to someone else is reported as `404`, exactly like one that does not exist (so ids cannot be guessed).
- [`ControllerOwnershipRulesTest`](../backend/src/test/java/com/example/ebookstore/controller/ControllerOwnershipRulesTest.java)
  scans **all** controllers (now and later) and fails if a method accepts a `userId` in a query, header or body,
  or exposes a JPA entity.

## Sample requests

Register (`201 Created`):

```json
POST /api/auth/register
{
  "fullName": "Asha Rao",
  "email": "asha.rao@example.com",
  "password": "Example-Passw0rd-123",
  "phone": "+91 98765 43210"
}
```

```json
{ "id": 1, "fullName": "Asha Rao", "email": "asha.rao@example.com", "phone": "+91 98765 43210",
  "role": "CUSTOMER", "createdAt": "2026-10-15T10:35:00Z" }
```

Log in (`200 OK`):

```json
POST /api/auth/login
{ "email": "asha.rao@example.com", "password": "Example-Passw0rd-123" }
```

```json
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 1800,
  "user": { "id": 1, "fullName": "Asha Rao", "email": "asha.rao@example.com", "phone": "+91 98765 43210",
            "role": "CUSTOMER", "createdAt": "2026-10-15T10:35:00Z" } }
```

Typical errors (all use the standard error body):

| Request | Status | `code` |
|---|---|---|
| Register with an e-mail that exists (any letter case) | 409 | `EMAIL_ALREADY_REGISTERED` |
| Register with a weak password or bad e-mail | 400 | `VALIDATION_FAILED` (see `fieldErrors`) |
| Register with an extra field such as `"role": "ADMIN"` | 400 | `VALIDATION_FAILED` |
| Wrong password or unknown e-mail | 401 | `INVALID_CREDENTIALS` |
| Call `/api/users/me` with no or a bad token | 401 | `UNAUTHORIZED` |
| Logged in but role not allowed | 403 | `FORBIDDEN` |

## Test the flow in Swagger UI

1. Start the backend (see [DATABASE_SETUP.md](DATABASE_SETUP.md)) and open <http://localhost:8080/swagger-ui.html>.
2. Open **Authentication → POST /api/auth/register**, click **Try it out**, keep the example body, click **Execute**. Expect `201`.
3. Open **POST /api/auth/login**, **Try it out**, **Execute**. Expect `200`. Copy the value of `accessToken` (without quotes).
4. Click the green **Authorize** button at the top, paste the token into the `bearerAuth` box, click **Authorize**, then **Close**.
   Swagger UI now adds `Authorization: Bearer <token>` to every request.
5. Open **GET /api/users/me**, **Try it out**, **Execute**. Expect `200` with your profile.
6. Click **Authorize → Logout** and execute `GET /api/users/me` again. Expect `401 UNAUTHORIZED`.

## Test the flow with PowerShell

```powershell
$base = "http://localhost:8080"

# 1. Register
$body = @{ fullName = "Asha Rao"; email = "asha.rao@example.com"; password = "Example-Passw0rd-123" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType "application/json" -Body $body

# 2. Log in and keep the token in a variable (do not print or save it)
$login = @{ email = "asha.rao@example.com"; password = "Example-Passw0rd-123" } | ConvertTo-Json
$token = (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" -Body $login).accessToken

# 3. Call a protected endpoint
Invoke-RestMethod -Uri "$base/api/users/me" -Headers @{ Authorization = "Bearer $token" }

# 4. Without a token you get 401
try { Invoke-RestMethod -Uri "$base/api/users/me" } catch { $_.Exception.Response.StatusCode.value__ }
```

With curl (Postman, Insomnia and similar tools work the same way: set the header `Authorization: Bearer <token>`):

```powershell
curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" `
  -d '{\"email\":\"asha.rao@example.com\",\"password\":\"Example-Passw0rd-123\"}'
curl.exe -s http://localhost:8080/api/users/me -H "Authorization: Bearer <paste-token-here>"
```

Use your own throw-away password for local tests; never reuse a real one.

## Using the token from React

Send `Authorization: Bearer <accessToken>` on every call to a protected endpoint. Keep the token in memory
(a React state or context). Avoid `localStorage` for production, because any script injected into the page can read it.
When the API answers `401`, discard the token and show the login page.

## Security notes

- `JWT_SECRET` must be at least 32 characters and come from an environment variable. Changing it logs everybody out.
- Tokens last 30 minutes and cannot be revoked before they expire, except by disabling or deleting the user
  (checked on every request). There is no refresh token yet.
- Passwords, tokens, hashes and e-mail addresses are never written to the log. Tests check this.
- Not built yet (documented in the contract): rate limiting of login and registration (429), password reset, e-mail verification, MFA.
- The security policy prefers SAML or OpenID Connect for user sign-in. This project uses first-party JWT as planned and
  approved for the capstone; moving to an OIDC provider later only changes how the token is issued and verified.
