package com.limiteddrop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.domain.Hold;
import com.limiteddrop.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {
    @Mock OutboxEventRepository repository;

    @Test
    void recordsACompleteStableEventPayload() throws Exception {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        Drop drop = new Drop("drop-1", "Drop", 5, now, null, now);
        Hold hold = new Hold("hold-1", drop, "customer-1", 2, now.plusSeconds(600), "key", now);

        new OutboxService(repository, new ObjectMapper()).record("hold.created", hold, now);

        ArgumentCaptor<com.limiteddrop.domain.OutboxEvent> captor = ArgumentCaptor.forClass(com.limiteddrop.domain.OutboxEvent.class);
        verify(repository).save(captor.capture());
        var event = captor.getValue();
        JsonNode payload = new ObjectMapper().readTree(event.getPayload());
        assertThat(event.getAggregateId()).isEqualTo("hold-1");
        assertThat(event.getEventType()).isEqualTo("hold.created");
        assertThat(event.getOccurredAt()).isEqualTo(now);
        assertThat(payload.get("eventId").asText()).isEqualTo(event.getId());
        assertThat(payload.get("holdId").asText()).isEqualTo("hold-1");
        assertThat(payload.get("dropId").asText()).isEqualTo("drop-1");
        assertThat(payload.get("customerId").asText()).isEqualTo("customer-1");
        assertThat(payload.get("quantity").asInt()).isEqualTo(2);
        assertThat(payload.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(payload.get("occurredAt").asText()).isEqualTo(now.toString());
    }

    @Test
    void usesUniqueEventIdsForSeparateRecords() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        Drop drop = new Drop("drop-1", "Drop", 5, now, null, now);
        Hold hold = new Hold("hold-1", drop, "customer-1", 1, now.plusSeconds(600), "key", now);
        OutboxService service = new OutboxService(repository, new ObjectMapper());

        service.record("hold.created", hold, now);
        service.record("hold.created", hold, now);

        ArgumentCaptor<com.limiteddrop.domain.OutboxEvent> captor = ArgumentCaptor.forClass(com.limiteddrop.domain.OutboxEvent.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(com.limiteddrop.domain.OutboxEvent::getId)
                .doesNotHaveDuplicates();
    }

    @Test
    void wrapsSerializationFailure() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("bad") { });
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        Drop drop = new Drop("drop-1", "Drop", 5, now, null, now);
        Hold hold = new Hold("hold-1", drop, "customer-1", 1, now.plusSeconds(600), "key", now);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new OutboxService(repository, mapper).record("hold.created", hold, now)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot serialize domain event");
        verifyNoInteractions(repository);
    }
}
