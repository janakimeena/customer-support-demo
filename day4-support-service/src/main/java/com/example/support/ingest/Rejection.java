package com.example.support.ingest;

/** A record that was not loaded, and why. Reported back to the caller instead of failing the whole run. */
public record Rejection(String origin, String reason) implements Normalized {
}
