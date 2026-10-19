package com.example.ebookstore.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.dto.CartItemResponse;
import com.example.ebookstore.dto.CartResponse;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.CartItem;

/**
 * Builds the cart DTOs and does all the cart arithmetic on the server.
 *
 * <p>Call these methods inside a transaction: the authors of a book are loaded lazily.
 */
@Component
public class CartMapper {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final BookMapper bookMapper;
    private final BusinessProperties business;

    public CartMapper(BookMapper bookMapper, BusinessProperties business) {
        this.bookMapper = bookMapper;
        this.business = business;
    }

    public CartResponse toResponse(List<CartItem> items) {
        List<CartItemResponse> lines = items.stream().map(this::toLine).toList();
        int totalQuantity = lines.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal subtotal = lines.stream().map(CartItemResponse::lineTotal).reduce(ZERO_MONEY, BigDecimal::add);
        return new CartResponse(lines, totalQuantity, subtotal);
    }

    private CartItemResponse toLine(CartItem item) {
        Book book = item.getBook();
        BigDecimal lineTotal = book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(item.getId(), summaryOf(book), item.getQuantity(), lineTotal, maxQuantityOf(book));
    }

    /** A book that is no longer sold is never reported as in stock, whatever its stock counter says. */
    private BookSummary summaryOf(Book book) {
        BookSummary s = bookMapper.toSummary(book);
        if (book.isActive()) {
            return s;
        }
        return new BookSummary(s.id(), s.title(), s.authors(), s.isbn(), s.price(), s.imageUrl(), s.ratingAvg(),
                s.ratingCount(), false, s.categoryId(), s.categoryName(), s.publisherId(), s.publisherName(),
                s.publishedYear());
    }

    private int maxQuantityOf(Book book) {
        return book.isActive() ? Math.min(book.getStockQuantity(), business.maxQuantityPerLine()) : 0;
    }
}
