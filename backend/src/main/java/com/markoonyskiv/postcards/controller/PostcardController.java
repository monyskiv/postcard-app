package com.markoonyskiv.postcards.controller;

import com.markoonyskiv.postcards.dto.PostcardMapper;
import com.markoonyskiv.postcards.dto.PostcardRequest;
import com.markoonyskiv.postcards.dto.PostcardResponse;
import com.markoonyskiv.postcards.dto.PostcardSummaryResponse;
import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.service.PostcardService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/postcards")
public class PostcardController {

    private final PostcardService postcardService;

    public PostcardController(PostcardService postcardService) {
        this.postcardService = postcardService;
    }

    @GetMapping
    public PagedModel<PostcardSummaryResponse> list(Pageable pageable) {
        return new PagedModel<>(postcardService.list(pageable).map(PostcardMapper::toSummary));
    }

    @GetMapping("/{id}")
    public PostcardResponse get(@PathVariable UUID id) {
        return PostcardMapper.toResponse(postcardService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PostcardResponse> create(@Valid @RequestBody PostcardRequest request) {
        Postcard created = postcardService.create(PostcardMapper.toEntity(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(PostcardMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public PostcardResponse update(@PathVariable UUID id, @Valid @RequestBody PostcardRequest request) {
        Postcard updated = postcardService.update(id, PostcardMapper.toEntity(request));
        return PostcardMapper.toResponse(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        postcardService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
