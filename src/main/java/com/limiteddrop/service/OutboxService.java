package com.limiteddrop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.OutboxEvent;
import com.limiteddrop.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) { this.repository = repository; this.objectMapper = objectMapper; }
    public void record(String eventType, Hold hold, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("holdId", hold.getId());
        payload.put("dropId", hold.getDrop().getId());
        payload.put("customerId", hold.getCustomerId());
        payload.put("quantity", hold.getQuantity());
        payload.put("state", hold.getState().name());
        payload.put("occurredAt", occurredAt.toString());
        try {
            repository.save(new OutboxEvent((String) payload.get("eventId"), hold.getId(), eventType, objectMapper.writeValueAsString(payload), occurredAt));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize domain event", e);
        }
    }
}
