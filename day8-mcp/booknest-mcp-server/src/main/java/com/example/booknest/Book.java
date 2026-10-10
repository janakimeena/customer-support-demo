package com.example.booknest;

import java.math.BigDecimal;

/**
 * One catalogue entry. Price is a BigDecimal, not a double (notes §7.3):
 * money needs exact decimal values.
 */
public record Book(String id, String title, String author, BigDecimal priceInr) {}
