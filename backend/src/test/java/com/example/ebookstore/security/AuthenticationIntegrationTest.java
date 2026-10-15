package com.example.ebookstore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.entity.Role;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * End-to-end test of authentication and authorization: the real application, real Spring Security, real JWTs and a
 * real (embedded) PostgreSQL. Security is never switched off. All secrets below are throw-away test values.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticationIntegrationTest.AdminOnlyController.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationIntegrationTest {

    private static final String JWT_SECRET = "test-only-secret-that-is-at-least-32-bytes-long-0123456789";
    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ registration

    @Test
    void registrationCreatesACustomerAndNeverReturnsOrStoresThePassword() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(register(email, PASSWORD, "+91 98765 43210"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.fullName").value("Asha Rao"))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCaseWith409() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());

        mockMvc.perform(register(email.toUpperCase(), PASSWORD, null))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidRegistrationReturns400WithFieldErrorsAndNeverEchoesThePassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"A\",\"email\":\"not-an-email\",\"password\":\"weakpass\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='fullName')]").exists())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("weakpass"));
    }

    @Test
    void clientCannotChooseARoleOrAnyOtherUnknownField() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Mallory\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                        + "\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Long.class, email))
                .isZero();
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginReturnsABearerTokenThatOpensUsersMe() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(login(email, PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.user.email").value(email))
            .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGeneric401() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());

        MvcResult wrongPassword = mockMvc.perform(login(email, "Wrong-Passw0rd-1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();
        MvcResult unknownEmail = mockMvc.perform(login(uniqueEmail(), PASSWORD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();

        assertThat(JsonPath.<String>read(wrongPassword.getResponse().getContentAsString(), "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownEmail.getResponse().getContentAsString(), "$.message"));
    }

    @Test
    void disabledAccountCannotLogInAndItsExistingTokenStopsWorking() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

        mockMvc.perform(login(email, PASSWORD)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedUserStopsWorking() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);

        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ protection of endpoints

    @Test
    void missingMalformedExpiredForgedAndAlgNoneTokensAllGetTheStandard401() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());
        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);

        String expired = signedToken(JWT_SECRET, userId, Role.CUSTOMER, Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600));
        String forged = signedToken("another-secret-that-is-also-32-bytes-long-abcdef", userId, Role.ADMIN,
                Instant.now(), Instant.now().plusSeconds(600));
        String algNone = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + b64("{\"sub\":\"" + userId
                + "\",\"iss\":\"ebookstore\",\"role\":\"ADMIN\"}") + ".";

        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.path").value("/api/users/me"));
        for (String bad : new String[] {"garbage", expired, forged, algNone}) {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void everyProtectedEndpointOfTheContractRejectsAnonymousCallers() throws Exception {
        String[][] protectedEndpoints = {
            {"GET", "/api/users/me"}, {"GET", "/api/cart"}, {"POST", "/api/cart/items"},
            {"PUT", "/api/cart/items/1"}, {"DELETE", "/api/cart/items/1"},
            {"GET", "/api/addresses"}, {"POST", "/api/addresses"}, {"PUT", "/api/addresses/1"},
            {"DELETE", "/api/addresses/1"}, {"POST", "/api/checkout"}, {"POST", "/api/payments/1/process"},
            {"GET", "/api/payments/1"}, {"GET", "/api/orders"}, {"GET", "/api/orders/1"},
            {"POST", "/api/orders/1/cancel"}, {"POST", "/api/orders/1/buy-again"},
            {"GET", "/api/gift-points/balance"}, {"GET", "/api/gift-points/transactions"},
            {"GET", "/api/recommendations"}};
        for (String[] endpoint : protectedEndpoints) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(org.springframework.http.HttpMethod.valueOf(endpoint[0]), endpoint[1])
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).startsWith("openapi: 3.0"));
        // Swagger UI redirects to its index page; it must not ask for a token.
        assertThat(mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus())
            .isNotIn(401, 403);
        assertThat(mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus())
            .isNotIn(401, 403);
        // The catalogue controller does not exist yet, so 404 (not 401) proves the URL is not protected.
        for (String path : new String[] {"/api/books", "/api/books/1", "/api/books/search", "/api/books/1/related",
                "/api/categories", "/api/publishers"}) {
            int httpStatus = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(httpStatus).as(path).isNotIn(401, 403);
        }
    }

    // ------------------------------------------------------------------ authorization (403) and ownership

    @Test
    void aCustomerGets403OnAnAdminOnlyEndpointButAnAdminGets200() throws Exception {
        String customerEmail = uniqueEmail();
        mockMvc.perform(register(customerEmail, PASSWORD, null)).andExpect(status().isCreated());
        String customerToken = loginAndGetToken(customerEmail);

        mockMvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.status").value(403));

        // Promote the user in the database (the only way to become an admin), then log in again.
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", customerEmail);
        mockMvc.perform(get("/api/test/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
            .andExpect(status().isOk());
    }

    @Test
    void theUserComesOnlyFromTheTokenNeverFromAnIdSentByTheClient() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        mockMvc.perform(register(emailA, PASSWORD, null)).andExpect(status().isCreated());
        mockMvc.perform(register(emailB, PASSWORD, null)).andExpect(status().isCreated());
        long idB = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, emailB);
        String tokenA = loginAndGetToken(emailA);

        // A tries to pass B's id in every place a client can: query string, header, path.
        mockMvc.perform(get("/api/users/me").param("userId", String.valueOf(idB)).param("id", String.valueOf(idB))
                .header("X-User-Id", String.valueOf(idB))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(emailA));
        mockMvc.perform(get("/api/users/" + idB).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void corsAllowsOnlyTheLocalReactDevelopmentServer() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    org.hamcrest.Matchers.containsString("POST")));

        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ------------------------------------------------------------------ logging

    @Test
    void passwordsTokensAndEmailsNeverAppearInTheLog(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, null)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);
        mockMvc.perform(login(email, "Wrong-Passw0rd-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"extra\":1}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain("Wrong-Passw0rd-1")
                .doesNotContain(token)
                .doesNotContain(JWT_SECRET)
                .doesNotContain(email);
    }

    // ------------------------------------------------------------------ helpers

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String email, String password, String phone) {
        String phoneJson = phone == null ? "" : ",\"phone\":\"" + phone + "\"";
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Rao\",\"email\":\"" + email + "\",\"password\":\"" + password + "\""
                        + phoneJson + "}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(login(email, PASSWORD)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a token the same way the application does, but with the given secret and times. */
    private static String signedToken(String secret, long userId, Role role, Instant issuedAt, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ebookstore").subject(String.valueOf(userId))
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("role", role.name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start embedded PostgreSQL", e);
        }
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        POSTGRES.close();
    }

    /** Test-only endpoint that proves role checks answer 403. It is not part of the application. */
    @RestController
    static class AdminOnlyController {

        @GetMapping("/api/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "ok";
        }
    }
}
