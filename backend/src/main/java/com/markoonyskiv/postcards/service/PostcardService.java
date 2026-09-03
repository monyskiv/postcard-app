package com.markoonyskiv.postcards.service;

import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.model.PostcardColor;
import com.markoonyskiv.postcards.repository.PostcardRepository;
import com.markoonyskiv.postcards.repository.PostcardSpecifications;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PostcardService {

    private final PostcardRepository postcardRepository;

    public PostcardService(PostcardRepository postcardRepository) {
        this.postcardRepository = postcardRepository;
    }

    public Page<Postcard> list(Pageable pageable) {
        return postcardRepository.findAll(pageable);
    }

    public Page<Postcard> search(String q, PostcardColor color, Integer year, String location, Pageable pageable) {
        List<Specification<Postcard>> specs = new ArrayList<>();
        if (StringUtils.hasText(q)) {
            specs.add(PostcardSpecifications.matchesQuery(q.trim()));
        }
        if (color != null) {
            specs.add(PostcardSpecifications.hasColor(color));
        }
        if (year != null) {
            specs.add(PostcardSpecifications.hasYear(year));
        }
        if (StringUtils.hasText(location)) {
            specs.add(PostcardSpecifications.matchesLocation(location.trim()));
        }

        Specification<Postcard> combined = specs.stream()
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
        return postcardRepository.findAll(combined, pageable);
    }

    public Postcard getById(UUID id) {
        return postcardRepository.findById(id)
                .orElseThrow(() -> new PostcardNotFoundException(id));
    }

    public Postcard create(Postcard postcard) {
        return postcardRepository.save(postcard);
    }

    public Postcard update(UUID id, Postcard updated) {
        Postcard existing = getById(id);
        existing.setTitle(updated.getTitle());
        existing.setYear(updated.getYear());
        existing.setAuthor(updated.getAuthor());
        existing.setDescription(updated.getDescription());
        existing.setColor(updated.getColor());
        existing.setLocation(updated.getLocation());
        existing.setFrontImageUrl(updated.getFrontImageUrl());
        existing.setBackImageUrl(updated.getBackImageUrl());
        return postcardRepository.save(existing);
    }

    public void delete(UUID id) {
        Postcard existing = getById(id);
        postcardRepository.delete(existing);
    }

    public Postcard save(Postcard postcard) {
        return postcardRepository.save(postcard);
    }
}
