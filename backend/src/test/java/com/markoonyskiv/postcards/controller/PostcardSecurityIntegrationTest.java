package com.markoonyskiv.postcards.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.markoonyskiv.postcards.dto.PostcardRequest;
import com.markoonyskiv.postcards.model.PostcardColor;
import com.markoonyskiv.postcards.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostcardSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

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
    void createPostcard_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/postcards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPostcard_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/postcards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPostcard_validToken_succeeds() throws Exception {
        String token = jwtService.generateToken("admin1@example.com");

        mockMvc.perform(post("/api/postcards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }
}
