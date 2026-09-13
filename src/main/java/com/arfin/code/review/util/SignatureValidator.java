package com.arfin.code.review.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
public class SignatureValidator {

    public static boolean isValid(byte[] payload, String signature, String secret) {
        try {
            String expected = "sha256=" + hmacSha256(payload, secret);
            boolean valid = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature == null ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8)
            );
            if (!valid) {
                log.warn("Webhook signature validation failed for payload length={} and signaturePresent={}",
                        payload == null ? 0 : payload.length, signature != null && !signature.isBlank());
            }
            return valid;
        } catch (Exception e) {
            log.error("Error while validating webhook signature", e);
            return false;
        }
    }

    private static String hmacSha256(byte[] data, String key) throws Exception {

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey =
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        mac.init(secretKey);

        byte[] rawHmac = mac.doFinal(data);

        StringBuilder hex = new StringBuilder();

        for (byte b : rawHmac) {
            String s = Integer.toHexString(0xff & b);
            if (s.length() == 1) hex.append('0');
            hex.append(s);
        }

        return hex.toString();
    }
}