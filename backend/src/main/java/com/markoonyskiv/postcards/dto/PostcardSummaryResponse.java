package com.markoonyskiv.postcards.dto;

import com.markoonyskiv.postcards.model.PostcardColor;
import java.util.UUID;

public record PostcardSummaryResponse(
        UUID id,
        String title,
        Integer year,
        PostcardColor color,
        String location,
        String frontImageUrl) {
}
