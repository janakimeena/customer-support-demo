package com.example.booknest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Plain business logic with no MCP in it. The tools, resources and prompts delegate here,
 * so the same catalogue could sit behind a REST controller too.
 *
 * <p>Demo data only; a real catalogue would come from an authorised database or API.
 */
@Component
public class BookCatalog {

    private final List<Book> books = List.of(
            new Book("B1", "Clean Code", "Robert C. Martin", new BigDecimal("450.00")),
            new Book("B2", "Head First Java", "Kathy Sierra", new BigDecimal("599.00")),
            new Book("B3", "Effective Java", "Joshua Bloch", new BigDecimal("720.00")),
            new Book("B4", "Java Concurrency in Practice", "Brian Goetz", new BigDecimal("850.00")),
            new Book("B5", "Designing Data-Intensive Applications", "Martin Kleppmann", new BigDecimal("1150.00"))
    );

    public List<Book> all() {
        return books;
    }

    /** Title or author contains the keyword, ignoring case. A blank query matches nothing. */
    public List<Book> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String keyword = query.strip().toLowerCase(Locale.ROOT);
        return books.stream()
                .filter(book -> book.title().toLowerCase(Locale.ROOT).contains(keyword)
                        || book.author().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
    }

    public Optional<Book> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return books.stream()
                .filter(book -> book.id().equalsIgnoreCase(id.strip()))
                .findFirst();
    }

    /** Exercise 4: books priced at most {@code maxPrice}. Rejects null and negative limits. */
    public List<Book> atMost(BigDecimal maxPrice) {
        if (maxPrice == null || maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maxPrice must be zero or more, got " + maxPrice);
        }
        return books.stream()
                .filter(book -> book.priceInr().compareTo(maxPrice) <= 0)
                .toList();
    }
}
