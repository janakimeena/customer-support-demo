package com.example.support.service;

/** Base class for "no such resource" errors; the API turns every subclass into a 404. */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }

    /** Short, stable problem title such as "Customer not found". */
    public abstract String getTitle();
}
