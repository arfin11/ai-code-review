package com.arfin.code.review.github;

import com.arfin.code.review.util.PemUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;

@Component
@Slf4j
public class GitHubJwtProvider {

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private Resource key;

    public String generateJwt() throws Exception {
        log.info("Generating GitHub App JWT for appId={}", appId);

        PrivateKey privateKey = PemUtils.readPrivateKey(
                new String(key.getInputStream().readAllBytes())
        );

        Instant now = Instant.now();

        String jwt = Jwts.builder()
                .setIssuer(appId)
                .setIssuedAt(Date.from(now.minusSeconds(60)))
                .setExpiration(Date.from(now.plusSeconds(600)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        log.debug("GitHub App JWT generated successfully for appId={}", appId);
        return jwt;
    }
}