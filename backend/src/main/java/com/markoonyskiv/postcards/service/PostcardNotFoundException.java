package com.markoonyskiv.postcards.service;

import java.util.UUID;

public class PostcardNotFoundException extends RuntimeException {

    public PostcardNotFoundException(UUID id) {
        super("Postcard not found: " + id);
    }
}
