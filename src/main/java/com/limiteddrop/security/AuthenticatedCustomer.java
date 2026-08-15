package com.limiteddrop.security;

/** Application-level identity derived only after Spring Security has validated the JWT. */
public record AuthenticatedCustomer(String id) { }
