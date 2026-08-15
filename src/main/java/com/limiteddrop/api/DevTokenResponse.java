package com.limiteddrop.api;

public record DevTokenResponse(String accessToken, String tokenType, long expiresIn) { }
