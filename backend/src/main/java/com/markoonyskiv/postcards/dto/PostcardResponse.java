package com.markoonyskiv.postcards.dto;

import com.markoonyskiv.postcards.model.PostcardColor;
import java.time.Instant;
import java.util.UUID;

public record PostcardResponse(
        UUID id,
        String title,
        Integer year,
        String author,
        String description,
        PostcardColor color,
        String location,
        String frontImageUrl,
        String backImageUrl,
        Instant createdAt,
        Instant updatedAt) {
}
