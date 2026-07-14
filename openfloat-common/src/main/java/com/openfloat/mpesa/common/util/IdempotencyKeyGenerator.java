package com.openfloat.mpesa.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for generating deterministic idempotency keys from M-Pesa transaction identifiers.
 * <p>
 * Combines one or more available identifiers (ConversationID, OriginatorConversationID,
 * CheckoutRequestID, MerchantRequestID) into a single SHA-256 hash used as a
 * deduplication key in Redis and the database.
 * </p>
 */
public final class IdempotencyKeyGenerator {

    private IdempotencyKeyGenerator() {
        // Utility class — no instantiation
    }

    /**
     * Generates an idempotency key by hashing all non-null, non-blank components.
     *
     * @param components the identifier components (any combination of ConversationID,
     *                   OriginatorConversationID, CheckoutRequestID, MerchantRequestID, etc.)
     * @return hex-encoded SHA-256 hash of the concatenated components
     * @throws IllegalArgumentException if all components are null or blank
     */
    public static String generate(String... components) {
        StringBuilder sb = new StringBuilder();
        for (String comp : components) {
            if (comp != null && !comp.isBlank()) {
                sb.append(comp).append("|");
            }
        }
        if (sb.isEmpty()) {
            throw new IllegalArgumentException("At least one non-blank component is required to generate an idempotency key");
        }
        return sha256(sb.toString());
    }

    /**
     * Convenience overload for the most common STK Push combination:
     * CheckoutRequestID + MerchantRequestID.
     *
     * @param checkoutRequestId  the Safaricom CheckoutRequestID
     * @param merchantRequestId  the Safaricom MerchantRequestID
     * @return hex-encoded SHA-256 idempotency key
     */
    public static String forStkPush(String checkoutRequestId, String merchantRequestId) {
        return generate(checkoutRequestId, merchantRequestId);
    }

    /**
     * Convenience overload for B2C and Reversal callbacks:
     * ConversationID + OriginatorConversationID.
     *
     * @param conversationId             the Safaricom ConversationID
     * @param originatorConversationId   the Safaricom OriginatorConversationID
     * @return hex-encoded SHA-256 idempotency key
     */
    public static String forAsyncCallback(String conversationId, String originatorConversationId) {
        return generate(conversationId, originatorConversationId);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
