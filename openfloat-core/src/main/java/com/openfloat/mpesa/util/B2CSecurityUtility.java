package com.openfloat.mpesa.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Utility component that encrypts the Safaricom B2C Initiator Password using
 * Safaricom's public certificate (RSA/ECB/PKCS1Padding).
 *
 * <p>Safaricom requires the {@code SecurityCredential} field in the B2C API
 * request to be the initiator password encrypted with their public key, then
 * Base64-encoded. This utility performs that transformation transparently.</p>
 *
 * <p>Phase 9 — Component 2: Safaricom B2C Initiator Password RSA Public Cert Encryption</p>
 */
@Slf4j
@Component
public class B2CSecurityUtility {

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String CERT_TYPE = "X.509";

    @Value("${openfloat.mpesa.b2c.initiator-password:Safaricom999!}")
    private String initiatorPassword;

    @Value("${openfloat.mpesa.b2c.certificate-path:classpath:certs/SandboxCertificate.cer}")
    private Resource certificateResource;

    /**
     * Returns the RSA-encrypted, Base64-encoded security credential required
     * by Safaricom's B2C API.
     *
     * <p>The credential is computed lazily on first call and cached. It remains
     * valid for the lifetime of the application (certificates do not change
     * between requests).</p>
     *
     * @return Base64-encoded encrypted security credential string
     * @throws IllegalStateException if the certificate cannot be loaded or
     *                               encryption fails
     */
    public String buildSecurityCredential() {
        try (InputStream certStream = certificateResource.getInputStream()) {
            return encryptInitiatorPassword(initiatorPassword, certStream);
        } catch (Exception e) {
            log.error("Failed to build B2C security credential from certificate [{}]: {}",
                    certificateResource.getFilename(), e.getMessage(), e);
            throw new IllegalStateException(
                    "Unable to encrypt B2C initiator password with Safaricom certificate", e);
        }
    }

    /**
     * Encrypts the given plain-text password using the public key extracted
     * from the supplied X.509 certificate input stream.
     *
     * @param plaintextPassword the raw initiator password
     * @param certInputStream   input stream of the Safaricom {@code .cer} file
     * @return Base64-encoded RSA-encrypted password
     * @throws Exception if certificate parsing or cipher operation fails
     */
    public String encryptInitiatorPassword(String plaintextPassword, InputStream certInputStream)
            throws Exception {
        log.debug("Encrypting B2C initiator password using Safaricom public certificate");

        // 1. Load X.509 certificate and extract RSA public key
        CertificateFactory factory = CertificateFactory.getInstance(CERT_TYPE);
        X509Certificate certificate = (X509Certificate) factory.generateCertificate(certInputStream);

        // 2. Initialise RSA cipher in ENCRYPT mode with Safaricom's public key
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, certificate.getPublicKey());

        // 3. Encrypt and Base64-encode the initiator password
        byte[] encrypted = cipher.doFinal(plaintextPassword.getBytes());
        String credential = Base64.getEncoder().encodeToString(encrypted);

        log.debug("B2C security credential generated successfully");
        return credential;
    }
}
