package com.example.ebookstore.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.GiftPointTransactionType;
import com.example.ebookstore.dto.GiftPointBalanceResponse;
import com.example.ebookstore.dto.GiftPointTransactionPage;
import com.example.ebookstore.entity.GiftPointTransaction;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.GiftPointMapper;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.UserRepository;

/**
 * Unit tests for {@link GiftPointServiceImpl}. Repositories are mocked — no database required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GiftPointServiceImplTest {

    private static final long USER_ID = 42L;

    private static final BusinessProperties BUSINESS = new BusinessProperties(
            10, 10, new BigDecimal("1.00"), 50, new BigDecimal("10.00"),
            48, 30, 24, 5, 2, List.of());

    @Mock
    private UserRepository userRepository;

    @Mock
    private GiftPointTransactionRepository ledger;

    private GiftPointServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GiftPointServiceImpl(userRepository, ledger, new GiftPointMapper(), BUSINESS);
    }

    // ------------------------------------------------------------------ getBalance

    @Test
    void getBalanceReturnsTheStoredBalanceWithBusinessRules() {
        when(userRepository.findPointsBalance(USER_ID)).thenReturn(Optional.of(150));

        GiftPointBalanceResponse resp = service.getBalance(USER_ID);

        assertThat(resp.balance()).isEqualTo(150);
        assertThat(resp.pointValue()).isEqualByComparingTo("1.00");
        assertThat(resp.maxRedeemPercentOfSubtotal()).isEqualTo(50);
        assertThat(resp.earnAmountPerPoint()).isEqualByComparingTo("10.00");
    }

    @Test
    void getBalanceWithZeroPointsIsValid() {
        when(userRepository.findPointsBalance(USER_ID)).thenReturn(Optional.of(0));

        assertThat(service.getBalance(USER_ID).balance()).isZero();
    }

    @Test
    void getBalanceThrowsUnauthorizedWhenUserNotFound() {
        when(userRepository.findPointsBalance(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBalance(USER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    // ------------------------------------------------------------------ listTransactions — no type filter

    @Test
    void listTransactionsWithoutTypeFilterReturnsAllEntriesForUser() {
        GiftPointTransaction tx = buildTransaction(1L, GiftPointTransactionType.EARN, 109, 109);
        when(ledger.findByUserId(eq(USER_ID), any())).thenReturn(new PageImpl<>(List.of(tx)));

        GiftPointTransactionPage page = service.listTransactions(USER_ID, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).points()).isEqualTo(109);
        assertThat(page.content().get(0).type()).isEqualTo(GiftPointTransactionType.EARN);
    }

    @Test
    void listTransactionsWithoutTypeFilterReturnsEmptyPageWhenNoTransactions() {
        when(ledger.findByUserId(eq(USER_ID), any())).thenReturn(new PageImpl<>(List.of()));

        GiftPointTransactionPage page = service.listTransactions(USER_ID, null, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // ------------------------------------------------------------------ listTransactions — with type filter

    @Test
    void listTransactionsWithRedeemTypeReturnsOnlyRedeemEntries() {
        GiftPointTransaction redeem = buildTransaction(2L, GiftPointTransactionType.REDEEM, -50, 60);
        when(ledger.findByUserIdAndType(eq(USER_ID), eq(GiftPointTransactionType.REDEEM), any()))
                .thenReturn(new PageImpl<>(List.of(redeem)));

        GiftPointTransactionPage page = service.listTransactions(USER_ID, GiftPointTransactionType.REDEEM, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).type()).isEqualTo(GiftPointTransactionType.REDEEM);
        assertThat(page.content().get(0).points()).isEqualTo(-50);
    }

    @Test
    void listTransactionsPageMetadataReflectsRepositoryPage() {
        // 3 total entries, page 0 size 2 → totalPages=2, first=true, last=false
        List<GiftPointTransaction> items = List.of(
                buildTransaction(10L, GiftPointTransactionType.EARN, 5, 5),
                buildTransaction(11L, GiftPointTransactionType.EARN, 10, 15));
        PageRequest pageable = PageRequest.of(0, 2);
        when(ledger.findByUserId(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(items, pageable, 3L));

        GiftPointTransactionPage page = service.listTransactions(USER_ID, null, 0, 2);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private static GiftPointTransaction buildTransaction(long id, GiftPointTransactionType type,
                                                         int points, int balanceAfter) {
        GiftPointTransaction tx = BeanUtils.instantiateClass(GiftPointTransaction.class);
        ReflectionTestUtils.setField(tx, "id", id);
        ReflectionTestUtils.setField(tx, "userId", USER_ID);
        ReflectionTestUtils.setField(tx, "type", type);
        ReflectionTestUtils.setField(tx, "points", points);
        ReflectionTestUtils.setField(tx, "balanceAfter", balanceAfter);
        ReflectionTestUtils.setField(tx, "createdAt", Instant.now());
        return tx;
    }
}
