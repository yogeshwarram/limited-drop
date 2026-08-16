package com.limiteddrop.request;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreateDropRequestTest {
    @Test
    void acceptsFutureDatesBeyondMysqlTimestampRange() {
        assertThat(new CreateDropRequest("Future", 1, Instant.parse("2099-01-01T00:00:00Z"), 60)
                .isOpensAtWithinMysqlDatetimeRange()).isTrue();
    }

    @Test
    void rejectsDatesOutsideMysqlDatetimeRange() {
        assertThat(new CreateDropRequest("Ancient", 1, Instant.parse("0999-12-31T23:59:59Z"), 60)
                .isOpensAtWithinMysqlDatetimeRange()).isFalse();
    }
}
