package com.example.support.api;

import java.util.Set;
import java.util.TreeSet;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, Set<String> allowed) {
        super(allowed.isEmpty()
                ? "This endpoint does not support sorting"
                : "Cannot sort by '" + property + "'; allowed: " + String.join(", ", new TreeSet<>(allowed)));
    }
}
