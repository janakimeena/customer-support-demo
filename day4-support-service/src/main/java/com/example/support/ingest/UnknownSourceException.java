package com.example.support.ingest;

import com.example.support.service.NotFoundException;

public class UnknownSourceException extends NotFoundException {

    public UnknownSourceException(String name) {
        super("No ingestion source named '" + name + "' is configured");
    }

    @Override
    public String getTitle() {
        return "Ingestion source not found";
    }
}
