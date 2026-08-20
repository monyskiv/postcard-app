package com.markoonyskiv.postcards.dto;

import com.markoonyskiv.postcards.model.Postcard;

public final class PostcardMapper {

    private PostcardMapper() {
    }

    public static Postcard toEntity(PostcardRequest request) {
        return new Postcard(
                request.title(),
                request.year(),
                request.author(),
                request.description(),
                request.color(),
                request.location(),
                request.frontImageUrl(),
                request.backImageUrl());
    }

    public static PostcardResponse toResponse(Postcard postcard) {
        return new PostcardResponse(
                postcard.getId(),
                postcard.getTitle(),
                postcard.getYear(),
                postcard.getAuthor(),
                postcard.getDescription(),
                postcard.getColor(),
                postcard.getLocation(),
                postcard.getFrontImageUrl(),
                postcard.getBackImageUrl(),
                postcard.getCreatedAt(),
                postcard.getUpdatedAt());
    }

    public static PostcardSummaryResponse toSummary(Postcard postcard) {
        return new PostcardSummaryResponse(
                postcard.getId(),
                postcard.getTitle(),
                postcard.getYear(),
                postcard.getColor(),
                postcard.getLocation(),
                postcard.getFrontImageUrl());
    }
}
