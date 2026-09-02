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

    public Postcard uploadImages(UUID id, MultipartFile front, MultipartFile back) {
        boolean hasFront = front != null && !front.isEmpty();
        boolean hasBack = back != null && !back.isEmpty();
        if (!hasFront && !hasBack) {
            throw new InvalidImageException("At least one of 'front' or 'back' image files must be provided");
        }

        Postcard postcard = postcardService.getById(id);
        if (hasFront) {
            postcard.setFrontImageUrl(imageStorageService.store(id, "front", front));
        }
        if (hasBack) {
            postcard.setBackImageUrl(imageStorageService.store(id, "back", back));
        }
        return postcardService.save(postcard);
    }
}
