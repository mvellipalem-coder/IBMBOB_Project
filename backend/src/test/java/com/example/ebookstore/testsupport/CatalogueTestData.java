package com.example.ebookstore.testsupport;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.entity.Author;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Category;
import com.example.ebookstore.entity.Publisher;

/**
 * Builds catalogue entities for unit tests. The entities are read-only in the application (no setters, protected
 * constructors), so tests fill them through reflection instead of adding test-only code to production classes.
 */
public final class CatalogueTestData {

    private CatalogueTestData() {
    }

    public static Category category(long id, String name) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "description", name + " books");
        return category;
    }

    public static Publisher publisher(long id, String name) {
        Publisher publisher = BeanUtils.instantiateClass(Publisher.class);
        ReflectionTestUtils.setField(publisher, "id", id);
        ReflectionTestUtils.setField(publisher, "name", name);
        ReflectionTestUtils.setField(publisher, "description", null);
        return publisher;
    }

    public static Author author(long id, String name) {
        Author author = BeanUtils.instantiateClass(Author.class);
        ReflectionTestUtils.setField(author, "id", id);
        ReflectionTestUtils.setField(author, "name", name);
        return author;
    }

    public static Book book(long id, String title, String price, int stock, Category category, Publisher publisher,
                            Author... authors) {
        Book book = BeanUtils.instantiateClass(Book.class);
        ReflectionTestUtils.setField(book, "id", id);
        ReflectionTestUtils.setField(book, "title", title);
        ReflectionTestUtils.setField(book, "isbn", String.format("%013d", 9780000000000L + id));
        ReflectionTestUtils.setField(book, "description", "About " + title);
        ReflectionTestUtils.setField(book, "price", new BigDecimal(price));
        ReflectionTestUtils.setField(book, "stockQuantity", stock);
        ReflectionTestUtils.setField(book, "ratingAvg", new BigDecimal("4.50"));
        ReflectionTestUtils.setField(book, "ratingCount", 12);
        ReflectionTestUtils.setField(book, "active", true);
        ReflectionTestUtils.setField(book, "category", category);
        ReflectionTestUtils.setField(book, "publisher", publisher);
        Set<Author> set = new LinkedHashSet<>(Set.of(authors));
        ReflectionTestUtils.setField(book, "authors", set);
        return book;
    }
}
