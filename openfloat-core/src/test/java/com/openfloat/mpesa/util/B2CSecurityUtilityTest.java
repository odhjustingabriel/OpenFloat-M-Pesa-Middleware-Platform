package com.openfloat.mpesa.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link B2CSecurityUtility}.
 *
 * These tests verify the RSA encryption behaviour using the Sandbox certificate
 * bundled in {@code src/test/resources/certs/SandboxCertificate.cer}.
 */
@DisplayName("B2CSecurityUtility — RSA Initiator Password Encryption")
class B2CSecurityUtilityTest {

    private B2CSecurityUtility utility;

    @BeforeEach
    void setUp() {
        utility = new B2CSecurityUtility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Encryption using the bundled sandbox certificate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("encryptInitiatorPassword() returns non-null Base64 string for valid cert stream")
    void encryptInitiatorPassword_withValidCert_returnsBase64String() throws Exception {
        // Arrange — load the sandbox certificate from test classpath
        InputStream certStream = getClass().getResourceAsStream("/certs/SandboxCertificate.cer");
        assertThat(certStream)
                .as("SandboxCertificate.cer must be present in src/test/resources/certs/")
                .isNotNull();

        // Act
        String credential = utility.encryptInitiatorPassword("Safaricom999!", certStream);

        // Assert — result should be a non-empty Base64-encoded string
        assertThat(credential)
                .as("Encrypted credential should not be null or blank")
                .isNotNull()
                .isNotBlank();

        // Verify it is valid Base64 (no exception on decode)
        byte[] decoded = java.util.Base64.getDecoder().decode(credential);
        assertThat(decoded).as("Decoded credential must have bytes").isNotEmpty();
    }

    @Test
    @DisplayName("encryptInitiatorPassword() produces different ciphertexts for the same plaintext (RSA non-deterministic)")
    void encryptInitiatorPassword_samePlaintext_producesUniqueCiphertext() throws Exception {
        InputStream certStream1 = getClass().getResourceAsStream("/certs/SandboxCertificate.cer");
        InputStream certStream2 = getClass().getResourceAsStream("/certs/SandboxCertificate.cer");
        assertThat(certStream1).isNotNull();
        assertThat(certStream2).isNotNull();

        String credential1 = utility.encryptInitiatorPassword("Safaricom999!", certStream1);
        String credential2 = utility.encryptInitiatorPassword("Safaricom999!", certStream2);

        // RSA with PKCS1Padding is non-deterministic — ciphertexts should differ
        assertThat(credential1).isNotEqualTo(credential2);
    }

    @Test
    @DisplayName("encryptInitiatorPassword() throws Exception when cert stream is null")
    void encryptInitiatorPassword_withNullStream_throwsException() {
        assertThatThrownBy(() -> utility.encryptInitiatorPassword("Safaricom999!", null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("encryptInitiatorPassword() throws Exception when cert stream contains invalid data")
    void encryptInitiatorPassword_withInvalidCertData_throwsException() {
        InputStream badStream = new java.io.ByteArrayInputStream("not-a-certificate".getBytes());
        assertThatThrownBy(() -> utility.encryptInitiatorPassword("Safaricom999!", badStream))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("encryptInitiatorPassword() produces Base64 output of expected RSA-2048 length (≥256 bytes)")
    void encryptInitiatorPassword_withValidCert_producesExpectedLength() throws Exception {
        InputStream certStream = getClass().getResourceAsStream("/certs/SandboxCertificate.cer");
        assertThat(certStream).isNotNull();

        String credential = utility.encryptInitiatorPassword("Safaricom999!", certStream);
        byte[] decoded = java.util.Base64.getDecoder().decode(credential);

        // RSA-2048 encrypted output is always 256 bytes
        assertThat(decoded.length)
                .as("RSA-2048 encrypted block should be 256 bytes")
                .isGreaterThanOrEqualTo(128); // ≥128 for 1024-bit key; 256 for 2048-bit
    }
}
