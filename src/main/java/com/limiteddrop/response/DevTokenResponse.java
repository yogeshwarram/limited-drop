package com.limiteddrop.response;

public record DevTokenResponse(String accessToken, String tokenType, long expiresIn) { }
