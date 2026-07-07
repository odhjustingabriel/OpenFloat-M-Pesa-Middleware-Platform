package com.openfloat.mpesa.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashing utility for audit log hash chaining.
 * <p>
 * Each audit log entry is hashed with the previous entry's hash,
 * creating a tamper-evident chain.
 * </p>
 */
public final class HashUtils {

    private static final String ALGORITHM = "SHA-256";

    private HashUtils() {
        // Utility class — no instantiation
    }

    /**
     * Computes SHA-256 hash of the given input.
     *
     * @param input the string to hash
     * @return hex-encoded SHA-256 hash
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a chained hash for audit log tamper detection.
     * Combines the previous hash with the current entry data.
     *
     * @param previousHash the hash of the previous audit log entry (empty string for first entry)
     * @param entryData    the serialized audit log entry data
     * @return hex-encoded SHA-256 hash of the chain
     */
    public static String chainedHash(String previousHash, String entryData) {
        String combined = previousHash + "|" + entryData;
        return sha256(combined);
    }
}
