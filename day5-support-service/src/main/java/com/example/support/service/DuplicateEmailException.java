package com.example.support.service;

public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException(String email) {
        super("A customer with email " + email + " already exists");
    }
}
