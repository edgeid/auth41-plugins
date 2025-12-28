package org.apifocal.auth41.common.validation;

import java.util.Collection;

/**
 * Common validation utilities for Auth41 plugins.
 */
public class ValidationUtils {

    private ValidationUtils() {
        // Utility class
    }

    /**
     * Check if a string is null or empty (after trimming).
     *
     * @param value the string to check
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Check if a string is not null and not empty.
     *
     * @param value the string to check
     * @return true if not null and not empty
     */
    public static boolean isNotEmpty(String value) {
        return !isNullOrEmpty(value);
    }

    /**
     * Require a value to be non-null, or return a default.
     *
     * @param value the value to check
     * @param defaultValue the default value if null
     * @param <T> the value type
     * @return the value or default
     */
    public static <T> T requireNonNullOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Require a string to be non-empty.
     *
     * @param value the string to check
     * @param message the exception message
     * @return the value if not empty
     * @throws IllegalArgumentException if null or empty
     */
    public static String requireNonEmpty(String value, String message) {
        if (isNullOrEmpty(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Require a collection to be non-empty.
     *
     * @param collection the collection to check
     * @param message the exception message
     * @param <T> the collection type
     * @return the collection if not empty
     * @throws IllegalArgumentException if null or empty
     */
    public static <T extends Collection<?>> T requireNonEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * Validate that a value is within a range.
     *
     * @param value the value to check
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @param fieldName the field name for error message
     * @return the value if valid
     * @throws IllegalArgumentException if out of range
     */
    public static int requireInRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                String.format("%s must be between %d and %d, got: %d", fieldName, min, max, value)
            );
        }
        return value;
    }

    /**
     * Validate an email address format (basic check).
     *
     * @param email the email to validate
     * @return true if valid email format
     */
    public static boolean isValidEmail(String email) {
        if (isNullOrEmpty(email)) {
            return false;
        }
        // Basic email validation
        return email.contains("@") && email.indexOf("@") > 0 && email.indexOf("@") < email.length() - 1;
    }

    /**
     * Extract domain from email address.
     *
     * @param email the email address
     * @return the domain part, or null if invalid
     */
    public static String extractDomain(String email) {
        if (!isValidEmail(email)) {
            return null;
        }
        int atIndex = email.indexOf("@");
        return email.substring(atIndex + 1).toLowerCase();
    }
}
