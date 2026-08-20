package com.markoonyskiv.postcards.repository;

import com.markoonyskiv.postcards.model.Postcard;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostcardRepository extends JpaRepository<Postcard, UUID> {
}
