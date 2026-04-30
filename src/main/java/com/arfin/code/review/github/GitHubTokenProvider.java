package com.arfin.code.review.github;

public interface GitHubTokenProvider {
    String getToken(int installationId);
}