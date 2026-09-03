package com.markoonyskiv.postcards.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.model.PostcardColor;
import com.markoonyskiv.postcards.repository.PostcardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostcardSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostcardRepository postcardRepository;

    @BeforeEach
    void seedPostcards() {
        postcardRepository.saveAndFlush(new Postcard(
                "Greetings from Lviv",
                1932,
                "Unknown",
                "A hand-tinted view of the old town square.",
                PostcardColor.COLOR,
                "Lviv, Ukraine",
                "https://example.com/lviv-front.jpg",
                "https://example.com/lviv-back.jpg"));
        postcardRepository.saveAndFlush(new Postcard(
                "Paris skyline",
                1925,
                "Jean Dupont",
                "A view of the Eiffel tower at dusk.",
                PostcardColor.BLACK_AND_WHITE,
                "Paris, France",
                "https://example.com/paris-front.jpg",
                "https://example.com/paris-back.jpg"));
        postcardRepository.saveAndFlush(new Postcard(
                "Winter in Odesa",
                1950,
                "Unknown",
                "A snowy view of the opera house.",
                PostcardColor.COLOR,
                "Odesa, Ukraine",
                "https://example.com/odesa-front.jpg",
                "https://example.com/odesa-back.jpg"));
    }

    @Test
    void search_matchesTitle_caseInsensitivePartial() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("q", "greetings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Greetings from Lviv"));
    }

    @Test
    void search_matchesAuthor_caseInsensitivePartial() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("q", "dupont"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Paris skyline"));
    }

    @Test
    void search_matchesLocation_caseInsensitivePartial() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("q", "paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].location").value("Paris, France"));
    }

    @Test
    void search_matchesDescription_caseInsensitivePartial() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("q", "eiffel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Paris skyline"));
    }

    @Test
    void search_filtersByColor() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("color", "BLACK_AND_WHITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Paris skyline"));
    }

    @Test
    void search_filtersByYear() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("year", "1950"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Winter in Odesa"));
    }

    @Test
    void search_noResults_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("q", "nonexistent-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void search_emptyQuery_returnsAllPostcards() throws Exception {
        mockMvc.perform(get("/api/postcards/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void search_noParamsAtAll_returnsAllPostcards() throws Exception {
        mockMvc.perform(get("/api/postcards/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }
}
