package com.example.ebookstore.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * Keeps the Java code and the approved openapi.yaml in agreement:
 * the {@link ErrorCode} enum must list exactly the codes the contract declares, and every code's HTTP status
 * must match the status of the contract's example for that code.
 */
class ErrorCodeContractTest {

    private static final OpenAPI SPEC = readSpec();

    @Test
    void enumMatchesTheErrorCodesDeclaredInTheContract() {
        Schema<?> codeSchema = (Schema<?>) SPEC.getComponents().getSchemas().get("ErrorResponse")
                .getProperties().get("code");
        Set<String> contractCodes = codeSchema.getEnum().stream().map(Object::toString).collect(Collectors.toSet());
        Set<String> javaCodes = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(javaCodes).isEqualTo(contractCodes);
    }

    @Test
    void everyCodeUsesTheHttpStatusShownInTheContractExamples() {
        List<JsonNode> examples = SPEC.getComponents().getExamples().values().stream()
                .map(e -> e.getValue())
                .filter(v -> v instanceof JsonNode node && node.has("code"))
                .map(v -> (JsonNode) v)
                .toList();

        assertThat(examples).isNotEmpty();
        for (JsonNode example : examples) {
            ErrorCode code = ErrorCode.valueOf(example.get("code").asText());
            assertThat(code.getStatus().value())
                    .as("HTTP status of %s", code)
                    .isEqualTo(example.get("status").asInt());
        }
    }

    private static OpenAPI readSpec() {
        String location = ErrorCodeContractTest.class.getClassLoader().getResource("openapi.yaml").toString();
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        return new OpenAPIV3Parser().readLocation(location, null, options).getOpenAPI();
    }
}
