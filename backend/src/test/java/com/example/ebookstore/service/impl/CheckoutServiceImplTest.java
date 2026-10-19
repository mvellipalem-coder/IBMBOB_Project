package com.example.ebookstore.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.dto.CheckoutRequest;

/**
 * Unit tests for the pure / static logic inside {@link CheckoutServiceImpl}.
 *
 * <p>The class has heavy database dependencies (13 repository/service collaborators) which make
 * a full constructor-based unit test impractical. Instead we:
 * <ul>
 *   <li>Test the static {@link CheckoutServiceImpl#hash} helper directly.</li>
 *   <li>Keep the full-flow coverage in {@link com.example.ebookstore.controller.CheckoutControllerIntegrationTest}.</li>
 * </ul>
 */
class CheckoutServiceImplTest {

    // ------------------------------------------------------------------ hash()

    @Test
    void hashIsDeterministicForTheSameRequest() {
        CheckoutRequest r1 = new CheckoutRequest(5L, PaymentMethod.COD, 100);
        CheckoutRequest r2 = new CheckoutRequest(5L, PaymentMethod.COD, 100);

        assertThat(CheckoutServiceImpl.hash(r1)).isEqualTo(CheckoutServiceImpl.hash(r2));
    }

    @Test
    void hashDiffersWhenAddressIdChanges() {
        CheckoutRequest base = new CheckoutRequest(1L, PaymentMethod.COD, 0);
        CheckoutRequest other = new CheckoutRequest(2L, PaymentMethod.COD, 0);

        assertThat(CheckoutServiceImpl.hash(base)).isNotEqualTo(CheckoutServiceImpl.hash(other));
    }

    @Test
    void hashDiffersWhenPaymentMethodChanges() {
        CheckoutRequest codReq   = new CheckoutRequest(1L, PaymentMethod.COD,  0);
        CheckoutRequest cardReq  = new CheckoutRequest(1L, PaymentMethod.CARD, 0);

        assertThat(CheckoutServiceImpl.hash(codReq)).isNotEqualTo(CheckoutServiceImpl.hash(cardReq));
    }

    @Test
    void hashDiffersWhenPointsToRedeemChange() {
        CheckoutRequest noPoints   = new CheckoutRequest(1L, PaymentMethod.COD, 0);
        CheckoutRequest withPoints = new CheckoutRequest(1L, PaymentMethod.COD, 50);

        assertThat(CheckoutServiceImpl.hash(noPoints)).isNotEqualTo(CheckoutServiceImpl.hash(withPoints));
    }

    @Test
    void nullPointsToRedeemTreatedAsZeroInHash() {
        // pointsOrZero() returns 0 when field is null — the hash must be identical to passing 0 explicitly
        CheckoutRequest nullPts  = new CheckoutRequest(3L, PaymentMethod.COD, null);
        CheckoutRequest zeroPts  = new CheckoutRequest(3L, PaymentMethod.COD, 0);

        assertThat(CheckoutServiceImpl.hash(nullPts)).isEqualTo(CheckoutServiceImpl.hash(zeroPts));
    }

    @Test
    void hashReturnsa64CharacterHexString() {
        String hash = CheckoutServiceImpl.hash(new CheckoutRequest(1L, PaymentMethod.CARD, 0));

        // SHA-256 produces 32 bytes = 64 hex characters
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    // ------------------------------------------------------------------ CheckoutRequest.pointsOrZero()

    @Test
    void pointsOrZeroReturnsZeroWhenPointsToRedeemIsNull() {
        assertThat(new CheckoutRequest(1L, PaymentMethod.COD, null).pointsOrZero()).isZero();
    }

    @Test
    void pointsOrZeroReturnsTheProvidedValue() {
        assertThat(new CheckoutRequest(1L, PaymentMethod.COD, 250).pointsOrZero()).isEqualTo(250);
    }
}
