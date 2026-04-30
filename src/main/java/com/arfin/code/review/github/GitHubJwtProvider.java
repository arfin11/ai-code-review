package com.arfin.code.review.github;

import com.arfin.code.review.util.PemUtils;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import io.jsonwebtoken.Jwts;
@Component
public class GitHubJwtProvider {

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private Resource key;

    public String generateJwt() throws Exception {

        PrivateKey privateKey = PemUtils.readPrivateKey(
                new String(key.getInputStream().readAllBytes())
        );

        Instant now = Instant.now();

        return Jwts.builder()
                .setIssuer(appId)
                .setIssuedAt(Date.from(now.minusSeconds(60)))
                .setExpiration(Date.from(now.plusSeconds(600)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
}