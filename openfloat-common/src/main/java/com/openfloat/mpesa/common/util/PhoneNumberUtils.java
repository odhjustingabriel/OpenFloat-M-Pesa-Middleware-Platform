package com.openfloat.mpesa.common.util;

import java.util.regex.Pattern;

/**
 * Utility for Safaricom MSISDN (phone number) validation and normalization.
 * <p>
 * Supports formats:
 * <ul>
 *   <li>254712345678 (international format without +)</li>
 *   <li>+254712345678 (international format with +)</li>
 *   <li>0712345678 (local Kenyan format)</li>
 * </ul>
 * All numbers are normalized to the format: 254XXXXXXXXX
 * </p>
 */
public final class PhoneNumberUtils {

    /** Regex for valid Kenyan MSISDN in international format (without +) */
    private static final Pattern MSISDN_PATTERN = Pattern.compile("^254[17]\\d{8}$");

    /** Regex for local Kenyan phone number */
    private static final Pattern LOCAL_PATTERN = Pattern.compile("^0[17]\\d{8}$");

    /** Regex for international format with + prefix */
    private static final Pattern INTL_PLUS_PATTERN = Pattern.compile("^\\+254[17]\\d{8}$");

    private PhoneNumberUtils() {
        // Utility class — no instantiation
    }

    /**
     * Normalizes a Kenyan phone number to Safaricom MSISDN format (254XXXXXXXXX).
     *
     * @param phoneNumber the phone number in any supported format
     * @return normalized MSISDN (e.g., "254712345678")
     * @throws IllegalArgumentException if the phone number format is invalid
     */
    public static String normalize(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        String cleaned = phoneNumber.trim().replaceAll("\\s+", "");

        // Already in correct format
        if (MSISDN_PATTERN.matcher(cleaned).matches()) {
            return cleaned;
        }

        // International format with +
        if (INTL_PLUS_PATTERN.matcher(cleaned).matches()) {
            return cleaned.substring(1);
        }

        // Local format (0712345678 → 254712345678)
        if (LOCAL_PATTERN.matcher(cleaned).matches()) {
            return "254" + cleaned.substring(1);
        }

        throw new IllegalArgumentException("Invalid Kenyan phone number format: " + phoneNumber);
    }

    /**
     * Validates whether the given string is a valid Kenyan MSISDN.
     *
     * @param phoneNumber the phone number to validate
     * @return true if valid in any accepted format
     */
    public static boolean isValid(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        String cleaned = phoneNumber.trim().replaceAll("\\s+", "");
        return MSISDN_PATTERN.matcher(cleaned).matches()
                || INTL_PLUS_PATTERN.matcher(cleaned).matches()
                || LOCAL_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Masks a phone number for display purposes (e.g., 254712***678).
     *
     * @param msisdn the normalized MSISDN
     * @return masked phone number
     */
    public static String mask(String msisdn) {
        if (msisdn == null || msisdn.length() < 9) {
            return "***";
        }
        return msisdn.substring(0, 6) + "***" + msisdn.substring(msisdn.length() - 3);
    }
}
