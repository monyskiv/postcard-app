package com.markoonyskiv.postcards.repository;

import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.model.PostcardColor;
import org.springframework.data.jpa.domain.Specification;

public final class PostcardSpecifications {

    private PostcardSpecifications() {
    }

    /** Matches title, author, location, or description (case-insensitive, partial). */
    public static Specification<Postcard> matchesQuery(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("author")), pattern),
                cb.like(cb.lower(root.get("location")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    public static Specification<Postcard> hasColor(PostcardColor color) {
        return (root, query, cb) -> cb.equal(root.get("color"), color);
    }

    public static Specification<Postcard> hasYear(Integer year) {
        return (root, query, cb) -> cb.equal(root.get("year"), year);
    }

    public static Specification<Postcard> matchesLocation(String location) {
        String pattern = "%" + location.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern);
    }
}
