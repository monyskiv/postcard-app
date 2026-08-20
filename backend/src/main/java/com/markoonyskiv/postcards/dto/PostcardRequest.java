package com.markoonyskiv.postcards.dto;

import com.markoonyskiv.postcards.model.PostcardColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostcardRequest(
        @NotBlank String title,
        Integer year,
        String author,
        String description,
        @NotNull PostcardColor color,
        String location,
        @NotBlank String frontImageUrl,
        @NotBlank String backImageUrl) {
}
