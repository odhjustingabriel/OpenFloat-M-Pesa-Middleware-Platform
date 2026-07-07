package com.openfloat.mpesa.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility for sensitive field protection.
 * <p>
 * Uses AES/GCM/NoPadding which provides both confidentiality and integrity.
 * Each encryption generates a unique 12-byte IV prepended to the ciphertext.
 * </p>
 */
public final class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private EncryptionUtils() {
        // Utility class — no instantiation
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plaintext  the text to encrypt
     * @param base64Key  the Base64-encoded 256-bit AES key
     * @return Base64-encoded ciphertext (IV prepended)
     */
    public static String encrypt(String plaintext, String base64Key) {
        try {
            SecretKey key = decodeKey(base64Key);
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts AES-256-GCM encrypted text.
     *
     * @param encryptedText  the Base64-encoded ciphertext (IV prepended)
     * @param base64Key      the Base64-encoded 256-bit AES key
     * @return the decrypted plaintext
     */
    public static String decrypt(String encryptedText, String base64Key) {
        try {
            SecretKey key = decodeKey(base64Key);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /**
     * Generates a cryptographically secure Base64-encoded 256-bit AES key.
     * Use this to generate keys for configuration.
     */
    public static String generateKey() {
        byte[] key = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static SecretKey decodeKey(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, ALGORITHM);
    }

    private static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
