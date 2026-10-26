package com.markoonyskiv.postcards.service;

import com.markoonyskiv.postcards.model.Postcard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostcardImageService {

    private final PostcardService postcardService;
    private final ImageStorageService imageStorageService;

    public PostcardImageService(PostcardService postcardService, ImageStorageService imageStorageService) {
        this.postcardService = postcardService;
        this.imageStorageService = imageStorageService;
    }

    public Postcard uploadImages(UUID id, MultipartFile front) {
        if (front == null || front.isEmpty()) {
            throw new InvalidImageException("A 'front' image file must be provided");
        }

        Postcard postcard = postcardService.getById(id);
        imageStorageService.deleteIfPresent(postcard.getFrontImageUrl());
        postcard.setFrontImageUrl(imageStorageService.store(id, "front", front));
        return postcardService.save(postcard);
    }

    public void deletePostcardAndImages(UUID id) {
        Postcard postcard = postcardService.getById(id);
        imageStorageService.deleteIfPresent(postcard.getFrontImageUrl());
        postcardService.delete(id);
    }
}
