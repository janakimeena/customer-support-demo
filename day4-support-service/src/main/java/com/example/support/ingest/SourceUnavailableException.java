package com.example.support.ingest;

/** A source couldn't be read (missing file, CRM down, bad response). The API answers 502 Bad Gateway. */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
