package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/** End-to-end test of the address API (real security, real PostgreSQL schema). */
@SpringBootTest
@AutoConfigureMockMvc
class AddressControllerIntegrationTest {

    private static final String PASSWORD = "Sup3r-Secret-Passw0rd";
    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("DB_USERNAME", () -> "postgres");
        registry.add("DB_PASSWORD", () -> "postgres");
        registry.add("JWT_SECRET", () -> "test-only-secret-that-is-at-least-32-bytes-long-0123456789");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerAndLogin();
        tokenB = registerAndLogin();
    }

    @Test
    void theFirstAddressBecomesTheDefaultAndIsReturnedWithALocationHeader() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content(address("Home", "411001", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recipientName").value("Home"))
            .andExpect(jsonPath("$.countryCode").value("IN"))     // default
            .andExpect(jsonPath("$.isDefault").value(true))       // first address
            .andReturn();
        long id = id(created);
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/addresses/" + id);

        mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void aSecondAddressIsNotDefaultUnlessRequestedAndOnlyOneDefaultEverExists() throws Exception {
        long first = create(tokenA, "First", null);
        long second = create(tokenA, "Second", false);
        assertThat(isDefault(first)).isTrue();
        assertThat(isDefault(second)).isFalse();

        long third = create(tokenA, "Third", true);
        assertThat(isDefault(first)).isFalse();
        assertThat(isDefault(second)).isFalse();
        assertThat(isDefault(third)).isTrue();
        assertThat(defaultCount()).isEqualTo(1);

        // Default first, then newest first.
        mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$[0].id").value(third))
            .andExpect(jsonPath("$[1].id").value(second))
            .andExpect(jsonPath("$[2].id").value(first));
    }

    @Test
    void updateReplacesAllFieldsMarkingDefaultMovesItButFalseNeverRemovesIt() throws Exception {
        long first = create(tokenA, "First", null);
        long second = create(tokenA, "Second", null);

        mockMvc.perform(put("/api/addresses/" + second).header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content(address("Renamed", "560001", true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipientName").value("Renamed"))
            .andExpect(jsonPath("$.postalCode").value("560001"))
            .andExpect(jsonPath("$.isDefault").value(true));
        assertThat(isDefault(first)).isFalse();

        mockMvc.perform(put("/api/addresses/" + second).header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content(address("Renamed", "560001", false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isDefault").value(true)); // false never removes the current default
        assertThat(defaultCount()).isEqualTo(1);
    }

    @Test
    void deletingTheDefaultMakesTheMostRecentlyCreatedRemainingAddressTheDefault() throws Exception {
        long first = create(tokenA, "First", null);
        long second = create(tokenA, "Second", null);
        long third = create(tokenA, "Third", null);

        mockMvc.perform(delete("/api/addresses/" + first).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isNoContent());

        assertThat(isDefault(third)).isTrue();
        assertThat(isDefault(second)).isFalse();

        // Deleting a non-default address changes no flag.
        mockMvc.perform(delete("/api/addresses/" + second).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isNoContent());
        assertThat(isDefault(third)).isTrue();
    }

    @Test
    void atMostTenAddressesPerUser() throws Exception {
        for (int i = 1; i <= 10; i++) {
            create(tokenA, "Address " + i, null);
        }
        mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content(address("Eleventh", "411001", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"))
            .andExpect(jsonPath("$.message").value("You can save at most 10 addresses."));

        // The limit is per user.
        create(tokenB, "Bob's first", null);
        // After a delete there is room again.
        long any = firstAddressId(tokenA);
        mockMvc.perform(delete("/api/addresses/" + any).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isNoContent());
        create(tokenA, "Replacement", null);
    }

    @Test
    void anotherUsersAddressCannotBeChangedDeletedOrSeen() throws Exception {
        long bobs = create(tokenB, "Bob", null);

        mockMvc.perform(put("/api/addresses/" + bobs).header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content(address("Hacked", "411001", true)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mockMvc.perform(delete("/api/addresses/" + bobs).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$").isEmpty());

        assertThat(jdbc.queryForObject("SELECT recipient_name FROM addresses WHERE id = ?", String.class, bobs))
                .isEqualTo("Bob");
    }

    @Test
    void invalidFieldsAndUnknownFieldsAreRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"recipientName":"","phone":"abc","line1":"x","city":"c","state":"s","postalCode":"!!",
                     "countryCode":"india"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.length()").value(4));

        mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(address("Home", "411001", null).replace("}", ",\"userId\":1}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(delete("/api/addresses/abc").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isBadRequest());
        // Nothing was saved for this user (the database is shared with the other tests, so ask through the API).
        mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void addressEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON)
                .content(address("Home", "411001", null))).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/addresses/1").contentType(MediaType.APPLICATION_JSON)
                .content(address("Home", "411001", null))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/addresses/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gift-points/balance")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gift-points/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void giftPointEndpointsShowAZeroBalanceAndAnEmptyLedgerForANewUserAndValidatePaging() throws Exception {
        mockMvc.perform(get("/api/gift-points/balance").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(0));
        mockMvc.perform(get("/api/gift-points/transactions").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.first").value(true));
        mockMvc.perform(get("/api/gift-points/transactions").param("size", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/gift-points/transactions").param("type", "BOGUS")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private static String address(String name, String postalCode, Boolean isDefault) {
        return "{\"recipientName\":\"" + name + "\",\"phone\":\"+91 98765 43210\",\"line1\":\"12 MG Road\","
                + "\"city\":\"Pune\",\"state\":\"Maharashtra\",\"postalCode\":\"" + postalCode + "\""
                + (isDefault == null ? "" : ",\"isDefault\":" + isDefault) + "}";
    }

    private long create(String token, String name, Boolean isDefault) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(address(name, "411001", isDefault)))
            .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private long firstAddressId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id");
        return id.longValue();
    }

    private static long id(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private boolean isDefault(long addressId) {
        return jdbc.queryForObject("SELECT is_default FROM addresses WHERE id = ?", Boolean.class, addressId);
    }

    private long defaultCount() {
        return jdbc.queryForObject("""
                SELECT max(c) FROM (SELECT count(*) FILTER (WHERE is_default) AS c FROM addresses GROUP BY user_id) t
                """, Long.class);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String registerAndLogin() throws Exception {
        String email = "addr-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Rao\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
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
}
