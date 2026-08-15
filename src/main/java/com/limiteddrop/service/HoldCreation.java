package com.limiteddrop.service;

import com.limiteddrop.response.HoldResponse;

/** A replay is successful but deliberately distinguished from a newly-created resource for HTTP semantics. */
public record HoldCreation(HoldResponse hold, boolean replayed) { }
