package com.example.ebookstore.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.BookDetail;
import com.example.ebookstore.dto.BookSummary;
import com.example.ebookstore.dto.CategoryResponse;
import com.example.ebookstore.dto.PublisherResponse;
import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Category;
import com.example.ebookstore.entity.Publisher;

/**
 * Converts catalogue entities into the DTOs of the API. Controllers never see the entities.
 *
 * <p>Call these methods inside a transaction: the authors of a book are loaded lazily.
 */
@Component
public class BookMapper {

    private final BusinessProperties business;

    public BookMapper(BusinessProperties business) {
        this.business = business;
    }

    public BookSummary toSummary(Book book) {
        return new BookSummary(book.getId(), book.getTitle(), authorNames(book), book.getIsbn(), book.getPrice(),
                book.getImageUrl(), book.getRatingAvg(), book.getRatingCount(), book.getStockQuantity() > 0,
                book.getCategory().getId(), book.getCategory().getName(),
                book.getPublisher().getId(), book.getPublisher().getName(), book.getPublishedYear());
    }

    public BookDetail toDetail(Book book) {
        BookSummary s = toSummary(book);
        // A customer can never order more than the stock or the per-line limit, whichever is smaller.
        int maxOrderable = Math.min(book.getStockQuantity(), business.maxQuantityPerLine());
        return new BookDetail(s.id(), s.title(), s.authors(), s.isbn(), s.price(), s.imageUrl(), s.ratingAvg(),
                s.ratingCount(), s.inStock(), s.categoryId(), s.categoryName(), s.publisherId(), s.publisherName(),
                s.publishedYear(), book.getDescription(), maxOrderable);
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    public PublisherResponse toResponse(Publisher publisher) {
        return new PublisherResponse(publisher.getId(), publisher.getName(), publisher.getDescription());
    }

    /** Author names in alphabetical order, so the JSON is the same on every call. */
    private static List<String> authorNames(Book book) {
        return book.getAuthors().stream().map(Author::getName).sorted().toList();
    }
}
