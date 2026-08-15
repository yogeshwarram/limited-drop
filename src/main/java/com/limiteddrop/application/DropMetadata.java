package com.limiteddrop.application;

import java.io.Serializable;
import java.time.Instant;
public record DropMetadata(String id, String title, int totalUnits, Instant opensAt, Integer holdDurationSeconds) implements Serializable { }
