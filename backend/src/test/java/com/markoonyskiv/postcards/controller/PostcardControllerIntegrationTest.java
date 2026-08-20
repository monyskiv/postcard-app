package com.markoonyskiv.postcards.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.markoonyskiv.postcards.dto.PostcardRequest;
import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.model.PostcardColor;
import com.markoonyskiv.postcards.repository.PostcardRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostcardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostcardRepository postcardRepository;

    private Postcard saveSamplePostcard() {
        Postcard postcard = new Postcard(
                "Greetings from Lviv",
                1932,
                "Unknown",
                "A hand-tinted view of the old town square.",
                PostcardColor.COLOR,
                "Lviv, Ukraine",
                "https://example.com/front.jpg",
                "https://example.com/back.jpg");
        return postcardRepository.saveAndFlush(postcard);
    }

    private PostcardRequest validRequest() {
        return new PostcardRequest(
                "Greetings from Odesa",
                1928,
                "Unknown",
                "A view of the opera house.",
                PostcardColor.BLACK_AND_WHITE,
                "Odesa, Ukraine",
                "https://example.com/odesa-front.jpg",
                "https://example.com/odesa-back.jpg");
    }

    @Test
    void createPostcard_returns201WithCreatedResource() throws Exception {
        PostcardRequest request = validRequest();

        mockMvc.perform(post("/api/postcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.title").value("Greetings from Odesa"))
                .andExpect(jsonPath("$.color").value("BLACK_AND_WHITE"));
    }

    @Test
    void createPostcard_missingRequiredFields_returns400() throws Exception {
        String invalidJson = """
                {"year": 1928}
                """;

        mockMvc.perform(post("/api/postcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPostcard_existing_returns200WithDetails() throws Exception {
        Postcard saved = saveSamplePostcard();

        mockMvc.perform(get("/api/postcards/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.title").value("Greetings from Lviv"))
                .andExpect(jsonPath("$.author").value("Unknown"))
                .andExpect(jsonPath("$.backImageUrl").value("https://example.com/back.jpg"));
    }

    @Test
    void getPostcard_missing_returns404() throws Exception {
        mockMvc.perform(get("/api/postcards/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPostcards_returnsPageOfSummaries() throws Exception {
        saveSamplePostcard();

        mockMvc.perform(get("/api/postcards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void updatePostcard_existing_returns200AndUpdates() throws Exception {
        Postcard saved = saveSamplePostcard();
        PostcardRequest request = validRequest();

        mockMvc.perform(put("/api/postcards/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.title").value("Greetings from Odesa"))
                .andExpect(jsonPath("$.color").value("BLACK_AND_WHITE"));
    }

    @Test
    void updatePostcard_missing_returns404() throws Exception {
        mockMvc.perform(put("/api/postcards/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePostcard_existing_returns204() throws Exception {
        Postcard saved = saveSamplePostcard();

        mockMvc.perform(delete("/api/postcards/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePostcard_missing_returns404() throws Exception {
        mockMvc.perform(delete("/api/postcards/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
