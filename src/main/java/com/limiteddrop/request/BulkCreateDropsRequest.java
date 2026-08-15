package com.limiteddrop.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkCreateDropsRequest(@NotEmpty @Size(max = 100) List<@Valid CreateDropRequest> drops) { }
