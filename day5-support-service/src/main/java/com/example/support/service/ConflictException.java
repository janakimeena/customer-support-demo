package com.example.support.service;

/** Base class for business-rule violations; the API turns every subclass into a 409. */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
