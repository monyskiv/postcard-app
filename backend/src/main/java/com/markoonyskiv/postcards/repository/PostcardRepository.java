package com.markoonyskiv.postcards.repository;

import com.markoonyskiv.postcards.model.Postcard;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PostcardRepository extends JpaRepository<Postcard, UUID>, JpaSpecificationExecutor<Postcard> {
}
