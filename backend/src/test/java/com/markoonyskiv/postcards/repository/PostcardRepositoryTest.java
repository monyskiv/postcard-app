package com.markoonyskiv.postcards.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.model.PostcardColor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostcardRepositoryTest {

    @Autowired
    private PostcardRepository postcardRepository;

    @Test
    void savesAndRetrievesAPostcard() {
        Postcard postcard = new Postcard(
                "Greetings from Lviv",
                1932,
                "Unknown",
                "A hand-tinted view of the old town square.",
                PostcardColor.COLOR,
                "Lviv, Ukraine",
                "https://example.com/front.jpg",
                "https://example.com/back.jpg");

        Postcard saved = postcardRepository.saveAndFlush(postcard);
        UUID id = saved.getId();

        assertThat(id).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Postcard> found = postcardRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Greetings from Lviv");
        assertThat(found.get().getYear()).isEqualTo(1932);
        assertThat(found.get().getAuthor()).isEqualTo("Unknown");
        assertThat(found.get().getColor()).isEqualTo(PostcardColor.COLOR);
        assertThat(found.get().getLocation()).isEqualTo("Lviv, Ukraine");
        assertThat(found.get().getFrontImageUrl()).isEqualTo("https://example.com/front.jpg");
        assertThat(found.get().getBackImageUrl()).isEqualTo("https://example.com/back.jpg");
    }
}
