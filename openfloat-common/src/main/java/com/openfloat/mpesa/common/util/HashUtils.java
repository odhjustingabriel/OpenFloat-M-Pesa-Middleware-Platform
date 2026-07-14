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

    /**
     * The canonical genesis hash used to seed the very first audit log entry.
     * This value is also used in {@code V3__audit_log_chain_seed.sql} to ensure
     * the DB-level seed matches the Java-level constant.
     */
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private HashUtils() {
        // Utility class — no instantiation
    }

    /**
     * Computes SHA-256 hash of the given input.
     *
     * @param input the string to hash; null is treated as an empty string
     * @return hex-encoded SHA-256 hash
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a chained hash for audit log tamper detection.
     * Combines the previous hash with the current entry data.
     * <p>
     * For the very first record, pass {@link #GENESIS_HASH} as {@code previousHash}.
     * </p>
     *
     * @param previousHash the hash of the previous audit log entry; null is treated as {@link #GENESIS_HASH}
     * @param entryData    the serialized audit log entry data; null is treated as an empty string
     * @return hex-encoded SHA-256 hash of the chain
     */
    public static String chainedHash(String previousHash, String entryData) {
        String prev = (previousHash != null && !previousHash.isBlank()) ? previousHash : GENESIS_HASH;
        String data = (entryData != null) ? entryData : "";
        return sha256(prev + "|" + data);
    }
}
