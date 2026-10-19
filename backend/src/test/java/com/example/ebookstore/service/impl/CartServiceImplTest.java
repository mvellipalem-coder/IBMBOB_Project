package com.example.ebookstore.service.impl;

import static com.example.ebookstore.testsupport.CatalogueTestData.author;
import static com.example.ebookstore.testsupport.CatalogueTestData.book;
import static com.example.ebookstore.testsupport.CatalogueTestData.category;
import static com.example.ebookstore.testsupport.CatalogueTestData.publisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.AddCartItemRequest;
import com.example.ebookstore.dto.AddToCartResult;
import com.example.ebookstore.dto.CartResponse;
import com.example.ebookstore.dto.UpdateCartItemRequest;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Cart;
import com.example.ebookstore.entity.CartItem;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.BookMapper;
import com.example.ebookstore.mapper.CartMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.CartItemRepository;
import com.example.ebookstore.repository.CartRepository;

/** Unit tests of the cart rules. The repositories are mocks, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final long USER_ID = 7;
    private static final long CART_ID = 70;

    private static final BusinessProperties BUSINESS = new BusinessProperties(10, 10, new BigDecimal("1.00"), 50,
            new BigDecimal("10.00"), 48, 30, 24, 5, 2, List.of());

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private BookRepository bookRepository;

    private CartServiceImpl service;

    private final Cart cart = cart(CART_ID, USER_ID);
    private final Book cleanCode = book(9, "Clean Code", "899.00", 2, category(4, "Programming"),
            publisher(6, "Pearson"), author(1, "Robert C. Martin"));
    private final Book pragmatic = book(11, "The Pragmatic Programmer", "949.00", 50, category(4, "Programming"),
            publisher(6, "Pearson"), author(2, "Andrew Hunt"));

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(cartRepository, cartItemRepository, bookRepository,
                new CartMapper(new BookMapper(BUSINESS), BUSINESS), BUSINESS);
    }

    // ------------------------------------------------------------------ add

    @Test
    void addingANewBookCreatesALineAndTheServerCalculatesTheTotals() {
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(cleanCode));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 9L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(line(31, cleanCode, 2)));

        AddToCartResult result = service.addItem(USER_ID, new AddCartItemRequest(9L, 2));

        assertThat(result.created()).isTrue();
        verify(cartRepository).insertIfAbsent(USER_ID);
        verify(cartItemRepository).save(any(CartItem.class));
        CartResponse cartResponse = result.cart();
        assertThat(cartResponse.totalQuantity()).isEqualTo(2);
        assertThat(cartResponse.items().get(0).lineTotal()).isEqualByComparingTo("1798.00");
        assertThat(cartResponse.subtotal()).isEqualByComparingTo("1798.00");
        assertThat(cartResponse.items().get(0).maxQuantity()).isEqualTo(2);
    }

    @Test
    void addingTheSameBookAgainAddsTheQuantitiesTogetherInsteadOfCreatingASecondLine() {
        CartItem existing = line(31, pragmatic, 3);
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(11L)).thenReturn(Optional.of(pragmatic));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 11L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(existing));

        AddToCartResult result = service.addItem(USER_ID, new AddCartItemRequest(11L, 4));

        assertThat(result.created()).isFalse();
        assertThat(existing.getQuantity()).isEqualTo(7);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void quantityDefaultsToOneWhenOmitted() {
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(11L)).thenReturn(Optional.of(pragmatic));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 11L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(line(31, pragmatic, 1)));

        service.addItem(USER_ID, new AddCartItemRequest(11L, null));

        verify(cartItemRepository).save(argThat(item -> item.getQuantity() == 1));
    }

    @Test
    void addingMoreThanTheStockIsRejectedWithAClearMessageAndNothingIsSaved() {
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(cleanCode));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(9L, 3)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.getMessage()).isEqualTo("Only 2 copies of \"Clean Code\" are available.");
                });
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void theStockCheckIncludesCopiesAlreadyInTheCart() {
        CartItem existing = line(31, cleanCode, 2);
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(cleanCode));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 9L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(9L, 1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        assertThat(existing.getQuantity()).isEqualTo(2);
    }

    @Test
    void anOutOfStockBookCannotBeAdded() {
        Book soldOut = book(12, "Sold Out", "100.00", 0, category(4, "Programming"), publisher(6, "Pearson"));
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(12L)).thenReturn(Optional.of(soldOut));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(12L, 1)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.getMessage()).isEqualTo("\"Sold Out\" is out of stock.");
                });
    }

    @Test
    void moreThanTenCopiesOfOneBookIsRejectedEvenWhenStockAllowsIt() {
        CartItem existing = line(31, pragmatic, 8);
        givenLockedCart();
        when(bookRepository.findByIdAndActiveTrue(11L)).thenReturn(Optional.of(pragmatic));
        when(cartItemRepository.findByCartIdAndBookId(CART_ID, 11L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(11L, 3)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CART_LINE_LIMIT_EXCEEDED));
        assertThat(existing.getQuantity()).isEqualTo(8);
    }

    @Test
    void anUnknownOrInactiveBookIsNotFoundAndNoCartIsCreated() {
        when(bookRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(99L, 1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
        verify(cartRepository, never()).insertIfAbsent(any());
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateSetsTheQuantityRatherThanAddingToIt() {
        CartItem existing = line(31, pragmatic, 5);
        givenLockedCart();
        when(cartItemRepository.findByIdAndCartId(31L, CART_ID)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(existing));

        CartResponse response = service.updateItem(USER_ID, 31, new UpdateCartItemRequest(2));

        assertThat(existing.getQuantity()).isEqualTo(2);
        assertThat(response.subtotal()).isEqualByComparingTo("1898.00");
    }

    @Test
    void updateAboveTheStockIsRejected() {
        CartItem existing = line(31, cleanCode, 1);
        givenLockedCart();
        when(cartItemRepository.findByIdAndCartId(31L, CART_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateItem(USER_ID, 31, new UpdateCartItemRequest(3)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        assertThat(existing.getQuantity()).isEqualTo(1);
    }

    @Test
    void aLineOfAnotherUserIsReportedAsNotFound() {
        givenLockedCart();
        // The lookup is scoped to the caller's cart id, so someone else's line is simply not found.
        when(cartItemRepository.findByIdAndCartId(500L, CART_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(USER_ID, 500, new UpdateCartItemRequest(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> service.removeItem(USER_ID, 500))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void aUserWithoutACartCannotUpdateOrRemoveAnything() {
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(USER_ID, 1))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    // ------------------------------------------------------------------ books that became unavailable

    @Test
    void aDeactivatedBookStaysVisibleWithMaxQuantityZeroButCannotBeIncreased() {
        CartItem existing = line(31, pragmatic, 2);
        ReflectionTestUtils.setField(pragmatic, "active", false);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(existing));

        CartResponse response = service.getCart(USER_ID);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).maxQuantity()).isZero();
        assertThat(response.items().get(0).book().inStock()).isFalse();

        givenLockedCart();
        when(cartItemRepository.findByIdAndCartId(31L, CART_ID)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.updateItem(USER_ID, 31, new UpdateCartItemRequest(3)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.getMessage()).contains("no longer available");
                });
    }

    @Test
    void aBookThatSoldOutAfterBeingAddedShowsMaxQuantityBelowTheQuantityInTheCart() {
        CartItem existing = line(31, cleanCode, 2);
        ReflectionTestUtils.setField(cleanCode, "stockQuantity", 1);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(existing));

        CartResponse response = service.getCart(USER_ID);

        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).maxQuantity()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ read

    @Test
    void aUserWithoutACartGetsAnEmptyCartAndNoCartIsCreated() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartResponse response = service.getCart(USER_ID);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalQuantity()).isZero();
        assertThat(response.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(cartRepository, never()).insertIfAbsent(any());
    }

    // ------------------------------------------------------------------ helpers

    private void givenLockedCart() {
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
    }

    private static Cart cart(long id, long userId) {
        Cart cart = BeanUtils.instantiateClass(Cart.class);
        ReflectionTestUtils.setField(cart, "id", id);
        ReflectionTestUtils.setField(cart, "userId", userId);
        return cart;
    }

    private static CartItem line(long id, Book book, int quantity) {
        CartItem item = new CartItem(CART_ID, book, quantity);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
