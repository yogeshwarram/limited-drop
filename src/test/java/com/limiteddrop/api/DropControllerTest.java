package com.limiteddrop.api;

import com.limiteddrop.application.DropQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropControllerTest {
    @Mock DropQueryService service;

    @Test
    void delegatesList() {
        DropResponse drop = new DropResponse("drop-1", "Drop", 10, 8, ControllerTestSupport.NOW, 300);
        when(service.list()).thenReturn(List.of(drop));

        assertThat(new DropController(service).list()).containsExactly(drop);
        verify(service).list();
    }

    @Test
    void delegatesGet() {
        DropResponse drop = new DropResponse("drop-1", "Drop", 10, 8, ControllerTestSupport.NOW, 300);
        when(service.get("drop-1")).thenReturn(drop);

        assertThat(new DropController(service).get("drop-1")).isEqualTo(drop);
        verify(service).get("drop-1");
    }
}
