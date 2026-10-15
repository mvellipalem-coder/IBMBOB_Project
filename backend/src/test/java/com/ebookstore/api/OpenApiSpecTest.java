package com.ebookstore.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * Automated review of src/main/resources/openapi.yaml: valid OpenAPI 3.0, no broken references,
 * every required operation present, and the security/consistency rules from the API requirements.
 */
class OpenApiSpecTest {

    private static final String SPEC = "openapi.yaml";

    /** The 31 operations that must exist: "METHOD path". */
    private static final Set<String> REQUIRED_OPERATIONS = Set.of(
        "POST /api/auth/register", "POST /api/auth/login", "GET /api/users/me",
        "GET /api/books", "GET /api/books/{id}", "GET /api/books/search", "GET /api/categories",
        "GET /api/publishers", "GET /api/books/{id}/related",
        "GET /api/cart", "POST /api/cart/items", "PUT /api/cart/items/{itemId}", "DELETE /api/cart/items/{itemId}",
        "GET /api/addresses", "POST /api/addresses", "PUT /api/addresses/{id}", "DELETE /api/addresses/{id}",
        "POST /api/checkout", "POST /api/payments/{orderId}/process", "GET /api/payments/{orderId}",
        "GET /api/orders", "GET /api/orders/{id}", "POST /api/orders/{id}/cancel", "POST /api/orders/{id}/buy-again",
        "GET /api/gift-points/balance", "GET /api/gift-points/transactions",
        "GET /api/recommendations");

    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
        "POST /api/auth/register", "POST /api/auth/login", "GET /api/books", "GET /api/books/{id}",
        "GET /api/books/search", "GET /api/categories", "GET /api/publishers", "GET /api/books/{id}/related");

    /** Request fields a client must never be able to send. */
    private static final Set<String> FORBIDDEN_REQUEST_FIELDS = Set.of(
        "total", "totalAmount", "subtotal", "price", "unitPrice", "lineTotal", "discount", "pointsDiscount",
        "status", "paymentStatus", "orderStatus", "role", "success", "paid", "transactionRef", "userId", "balance");

    private static SwaggerParseResult result;
    private static OpenAPI api;

    @BeforeAll
    static void parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(false); // keep $ref objects so reference checks see them
        result = new OpenAPIV3Parser().readLocation(SPEC, null, options);
        api = result.getOpenAPI();
    }

    // ------------------------------------------------------------------ validity

    @Test
    void specIsValidOpenApi30WithoutParserMessages() {
        assertThat(api).as("spec could not be parsed: %s", result.getMessages()).isNotNull();
        assertThat(api.getOpenapi()).startsWith("3.0.");
        assertThat(result.getMessages()).as("parser messages (errors/warnings)").isEmpty();
    }

    @Test
    void everyReferenceResolvesAndEveryComponentIsUsed() {
        String raw = readSpec();
        Components components = api.getComponents();
        Set<String> defined = new HashSet<>();
        addAll(defined, "schemas", components.getSchemas());
        addAll(defined, "parameters", components.getParameters());
        addAll(defined, "responses", components.getResponses());
        addAll(defined, "examples", components.getExamples());
        addAll(defined, "headers", components.getHeaders());

        Set<String> referenced = new HashSet<>();
        var matcher = java.util.regex.Pattern.compile("\\$ref:\\s*'#/components/([a-zA-Z]+)/([A-Za-z0-9_.-]+)'").matcher(raw);
        while (matcher.find()) {
            referenced.add(matcher.group(1) + "/" + matcher.group(2));
        }

        assertThat(referenced).as("$ref targets that do not exist").isSubsetOf(defined);
        Set<String> unused = new HashSet<>(defined);
        unused.removeAll(referenced);
        assertThat(unused).as("components defined but never referenced").isEmpty();
    }

    // ------------------------------------------------------------------ coverage

    @Test
    void allRequiredOperationsExistAndNothingExtraIsExposed() {
        Set<String> actual = operations().keySet();
        assertThat(actual).containsAll(REQUIRED_OPERATIONS);
        assertThat(actual).as("operations not in the requirement list").isSubsetOf(REQUIRED_OPERATIONS);
    }

    @Test
    void everyOperationIsDocumentedConsistently() {
        Set<String> operationIds = new HashSet<>();
        operations().forEach((name, op) -> {
            assertThat(op.getOperationId()).as(name + " operationId").isNotBlank();
            assertThat(operationIds.add(op.getOperationId())).as(name + " operationId unique").isTrue();
            assertThat(op.getSummary()).as(name + " summary").isNotBlank();
            assertThat(op.getTags()).as(name + " tags").isNotEmpty();
            assertThat(op.getResponses()).as(name + " responses").containsKey("500");
            // every JSON response must carry at least one example
            op.getResponses().forEach((code, response) -> assertThat(hasExampleOrNoBody(response, code))
                .as(name + " response " + code + " example").isTrue());
        });
    }

    // ------------------------------------------------------------------ security

    @Test
    void authenticationRequirementsAreExplicit() {
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(api.getSecurity()).as("global default must require bearerAuth").hasSize(1);
        assertThat(api.getSecurity().get(0)).containsKey("bearerAuth");

        operations().forEach((name, op) -> {
            boolean isPublic = op.getSecurity() != null && op.getSecurity().isEmpty();
            if (PUBLIC_OPERATIONS.contains(name)) {
                assertThat(isPublic).as(name + " must be public (security: [])").isTrue();
                assertThat(op.getResponses()).as(name + " public: no 401 for missing token")
                    .doesNotContainKey("403");
            } else {
                assertThat(op.getSecurity()).as(name + " must inherit bearerAuth").isNull();
                assertThat(op.getResponses()).as(name + " needs 401").containsKey("401");
            }
        });
        // login may legitimately return 401 for bad credentials even though it is public
        assertThat(operations().get("POST /api/auth/login").getResponses()).containsKey("401");
    }

    @Test
    void requestBodiesRejectUnknownFieldsAndForbiddenMoneyOrStatusFields() {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        List<String> requestSchemas = operations().values().stream()
            .filter(op -> op.getRequestBody() != null)
            .map(op -> refName(op.getRequestBody().getContent().get("application/json").getSchema()))
            .toList();
        assertThat(requestSchemas).isNotEmpty();

        for (String name : requestSchemas) {
            Schema<?> schema = schemas.get(name);
            assertThat(schema.getAdditionalProperties()).as(name + " additionalProperties").isEqualTo(Boolean.FALSE);
            Set<String> fields = schema.getProperties() == null ? Set.of() : schema.getProperties().keySet();
            assertThat(fields).as(name + " must not accept server-owned fields").doesNotContainAnyElementsOf(FORBIDDEN_REQUEST_FIELDS);
        }
    }

    @Test
    void credentialsAreWriteOnlyAndNeverReturned() {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        assertThat(property("RegisterRequest", "password").getWriteOnly()).isTrue();
        assertThat(property("LoginRequest", "password").getWriteOnly()).isTrue();
        for (var entry : schemas.entrySet()) {
            if (!entry.getKey().endsWith("Request") && entry.getValue().getProperties() != null) {
                assertThat(entry.getValue().getProperties().keySet())
                    .as(entry.getKey() + " is a response/DTO and must not expose secrets or internals")
                    .doesNotContain("password", "passwordHash", "password_hash", "version", "active", "stockQuantity");
            }
        }
    }

    @Test
    void checkoutAndPaymentRequireIdempotencyKeyAndPaymentHasNoBody() {
        for (String name : List.of("POST /api/checkout", "POST /api/payments/{orderId}/process")) {
            Operation op = operations().get(name);
            assertThat(op.getParameters().stream().map(p -> p.get$ref()))
                .as(name + " idempotency key").contains("#/components/parameters/IdempotencyKey");
            assertThat(op.getResponses()).as(name).containsKeys("200".equals(statusOf(name)) ? "200" : "201", "422", "409");
        }
        Parameter key = api.getComponents().getParameters().get("IdempotencyKey");
        assertThat(key.getRequired()).isTrue();
        assertThat(key.getIn()).isEqualTo("header");
        assertThat(operations().get("POST /api/payments/{orderId}/process").getRequestBody())
            .as("payment outcome and amount must come from the server, never from a request body").isNull();
    }

    @Test
    void checkoutSupportsOptionalGiftPointRedemption() {
        Schema<?> checkout = api.getComponents().getSchemas().get("CheckoutRequest");
        assertThat(checkout.getRequired()).containsExactlyInAnyOrder("addressId", "paymentMethod");
        assertThat(checkout.getProperties()).containsKey("pointsToRedeem");
        assertThat(api.getComponents().getSchemas().get("PaymentMethod").getEnum())
            .containsExactlyInAnyOrder("CARD", "UPI", "COD");
    }

    // ------------------------------------------------------------------ business rules

    @Test
    void cancellationRuleIsDocumentedWithStatusesAndTimeWindow() {
        Operation cancel = operations().get("POST /api/orders/{id}/cancel");
        String text = cancel.getDescription();
        assertThat(text).contains("48 hours", "PLACED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED");
        assertThat(cancel.getResponses().get("409").getContent().get("application/json").getExamples())
            .containsKeys("alreadyCancelled", "wrongStatus", "windowExpired");
        assertThat(api.getComponents().getSchemas().get("OrderStatus").getEnum())
            .containsExactly("PLACED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED");
    }

    @Test
    void cancellationReasonsMatchErrorCodes() {
        List<String> reasons = enumValues(property("CancellationInfo", "reason")).stream()
            .filter(java.util.Objects::nonNull).toList();
        assertThat(enumValues(property("ErrorResponse", "code"))).containsAll(reasons);
    }

    @Test
    void bookListingsSupportPaginationFiltersAndSorting() {
        for (String name : List.of("GET /api/books", "GET /api/books/search")) {
            List<String> params = operations().get(name).getParameters().stream().map(Parameter::get$ref).toList();
            assertThat(params).contains(
                "#/components/parameters/Page", "#/components/parameters/Size",
                "#/components/parameters/CategoryId", "#/components/parameters/PublisherId",
                "#/components/parameters/MinPrice", "#/components/parameters/MaxPrice");
        }
        assertThat(api.getComponents().getParameters().get("Size").getSchema().getMaximum().intValue()).isEqualTo(100);
    }

    @Test
    void moneyIsNeverAFloatAndAlwaysHasTwoDecimals() {
        Schema<?> money = api.getComponents().getSchemas().get("MoneyAmount");
        assertThat(money.getType()).isEqualTo("number");
        assertThat(money.getMultipleOf()).isEqualByComparingTo("0.01");
        assertThat(money.getMinimum()).isEqualByComparingTo("0");
        // no money-looking property may bypass MoneyAmount with a raw float/double
        api.getComponents().getSchemas().forEach((schemaName, schema) -> {
            if (schema.getProperties() == null) {
                return;
            }
            ((Map<String, Schema>) schema.getProperties()).forEach((prop, value) -> {
                if (value.get$ref() == null && "number".equals(value.getType())) {
                    assertThat(value.getFormat()).as(schemaName + "." + prop).isNotIn("float", "double");
                }
            });
        });
    }

    @Test
    void errorExamplesMatchTheirHttpStatusAndAreDeclaredCodes() {
        List<String> codes = enumValues(property("ErrorResponse", "code"));
        Set<String> usedCodes = new HashSet<>();
        String raw = readSpec();

        api.getComponents().getExamples().forEach((exampleName, example) -> {
            if (!(example.getValue() instanceof com.fasterxml.jackson.databind.JsonNode value) || !value.has("code")) {
                return;
            }
            String code = value.get("code").asText();
            assertThat(codes).as(exampleName + " code").contains(code);
            usedCodes.add(code);
            assertThat(reasonPhraseOk(value.get("status").asInt(), value.get("error").asText()))
                .as(exampleName + " status/error text").isTrue();
        });
        assertThat(usedCodes).as("declared error codes with no example").containsAll(new HashSet<>(codes));
        assertThat(raw).doesNotContain("stackTrace", "Exception", "SELECT ");
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Operation> operations() {
        Map<String, Operation> all = new java.util.LinkedHashMap<>();
        api.getPaths().forEach((path, item) -> {
            for (Map.Entry<PathItem.HttpMethod, Operation> e : item.readOperationsMap().entrySet()) {
                all.put(e.getKey() + " " + path, e.getValue());
            }
        });
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Schema<Object> property(String schemaName, String propertyName) {
        Map<String, Schema<Object>> props = (Map<String, Schema<Object>>) (Map<?, ?>)
            api.getComponents().getSchemas().get(schemaName).getProperties();
        return props.get(propertyName);
    }

    private static List<String> enumValues(Schema<?> schema) {
        return schema.getEnum().stream().map(v -> v == null ? null : v.toString()).collect(Collectors.toList());
    }

    private static String statusOf(String operation) {
        return operation.equals("POST /api/payments/{orderId}/process") ? "200" : "201";
    }

    private static boolean hasExampleOrNoBody(ApiResponse response, String code) {
        if (response.get$ref() != null) {
            ApiResponse target = api.getComponents().getResponses().get(response.get$ref().substring(response.get$ref().lastIndexOf('/') + 1));
            return hasExampleOrNoBody(target, code);
        }
        if (response.getContent() == null) {
            return code.equals("204");
        }
        MediaType json = response.getContent().get("application/json");
        return json != null && ((json.getExamples() != null && !json.getExamples().isEmpty()) || json.getExample() != null);
    }

    private static String refName(Schema<?> schema) {
        String ref = schema.get$ref();
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private static void addAll(Set<String> target, String kind, Map<String, ?> items) {
        if (items != null) {
            items.keySet().forEach(name -> target.add(kind + "/" + name));
        }
    }

    private static boolean reasonPhraseOk(int status, String text) {
        return switch (status) {
            case 400 -> text.equals("Bad Request");
            case 401 -> text.equals("Unauthorized");
            case 404 -> text.equals("Not Found");
            case 409 -> text.equals("Conflict");
            case 422 -> text.equals("Unprocessable Entity");
            case 429 -> text.equals("Too Many Requests");
            case 500 -> text.equals("Internal Server Error");
            default -> false;
        };
    }

    private static String readSpec() {
        try (var in = OpenApiSpecTest.class.getClassLoader().getResourceAsStream(SPEC)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
