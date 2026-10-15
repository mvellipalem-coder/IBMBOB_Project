package com.example.ebookstore.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Checks the e-mail and password rules of the approved contract. */
class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void aValidRequestHasNoViolations() {
        assertThat(violatedFields(new RegisterRequest("Asha Rao", "asha.rao@example.com", "Example-Passw0rd-123",
                "+91 98765 43210"))).isEmpty();
    }

    @Test
    void phoneIsOptional() {
        assertThat(violatedFields(new RegisterRequest("Asha Rao", "asha@example.com", "Abcdefg1", null))).isEmpty();
    }

    @Test
    void rejectsBadEmails() {
        for (String email : new String[] {"", "plainaddress", "a@b", "a b@example.com", "@example.com",
                "a@@example.com", "a@example..com"}) {
            assertThat(violatedFields(new RegisterRequest("Asha Rao", email, "Abcdefg1", null)))
                    .as("email '%s'", email).contains("email");
        }
    }

    @Test
    void rejectsWeakOrOutOfRangePasswords() {
        for (String password : new String[] {"", "Short1a", "alllowercase1", "ALLUPPERCASE1", "NoDigitsHere",
                "Aa1" + "x".repeat(70)}) {
            assertThat(violatedFields(new RegisterRequest("Asha Rao", "asha@example.com", password, null)))
                    .as("password of length %d", password.length()).contains("password");
        }
    }

    @Test
    void rejectsPasswordsLongerThan72BytesEvenWhenTheyHaveFewerCharacters() {
        // 40 characters but 77 bytes in UTF-8: BCrypt would silently ignore the tail.
        String password = "Aa1" + "\u00e9".repeat(37);
        assertThat(password.length()).isLessThanOrEqualTo(72);
        assertThat(violatedFields(new RegisterRequest("Asha Rao", "asha@example.com", password, null)))
                .contains("password");
    }

    @Test
    void rejectsShortNamesAndBadPhones() {
        assertThat(violatedFields(new RegisterRequest("A", "asha@example.com", "Abcdefg1", null))).contains("fullName");
        assertThat(violatedFields(new RegisterRequest(" ", "asha@example.com", "Abcdefg1", null))).contains("fullName");
        assertThat(violatedFields(new RegisterRequest("Asha", "asha@example.com", "Abcdefg1", "abc"))).contains("phone");
    }

    @Test
    void loginRequiresEmailAndPassword() {
        assertThat(violatedFields(new LoginRequest("", ""))).contains("email", "password");
        assertThat(violatedFields(new LoginRequest("asha@example.com", "x"))).isEmpty();
    }

    private static Set<String> violatedFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }
}
