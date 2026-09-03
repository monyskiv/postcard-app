package com.markoonyskiv.postcards.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.markoonyskiv.postcards.model.Postcard;
import com.markoonyskiv.postcards.model.PostcardColor;
import com.markoonyskiv.postcards.repository.PostcardRepository;
import com.markoonyskiv.postcards.security.JwtService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.r2.bucket-name=test-bucket",
        "app.r2.public-base-url=https://pub-test.r2.dev"
})
class PostcardImageUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostcardRepository postcardRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private S3Client r2Client;

    private String authHeader() {
        return "Bearer " + jwtService.generateToken("admin1@example.com");
    }

    private Postcard saveSamplePostcard() {
        Postcard postcard = new Postcard(
                "Greetings from Lviv",
                1932,
                "Unknown",
                "A hand-tinted view of the old town square.",
                PostcardColor.COLOR,
                "Lviv, Ukraine",
                "https://example.com/old-front.jpg",
                "https://example.com/old-back.jpg");
        return postcardRepository.saveAndFlush(postcard);
    }

    private static final String EXISTING_FRONT_KEY = "postcards/existing/front-abc123.jpg";
    private static final String EXISTING_BACK_KEY = "postcards/existing/back-def456.jpg";

    private Postcard savePostcardWithR2Images() {
        Postcard postcard = new Postcard(
                "Greetings from Lviv",
                1932,
                "Unknown",
                "A hand-tinted view of the old town square.",
                PostcardColor.COLOR,
                "Lviv, Ukraine",
                "https://pub-test.r2.dev/" + EXISTING_FRONT_KEY,
                "https://pub-test.r2.dev/" + EXISTING_BACK_KEY);
        return postcardRepository.saveAndFlush(postcard);
    }

    private byte[] samplePngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void uploadImages_validToken_updatesPostcardUrlsInR2AndDatabase() throws Exception {
        Postcard saved = saveSamplePostcard();
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", samplePngBytes(200, 100));
        MockMultipartFile back = new MockMultipartFile("back", "back.png", "image/png", samplePngBytes(200, 100));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .file(back)
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frontImageUrl")
                        .value(startsWith("https://pub-test.r2.dev/postcards/" + saved.getId() + "/front-")))
                .andExpect(jsonPath("$.backImageUrl")
                        .value(startsWith("https://pub-test.r2.dev/postcards/" + saved.getId() + "/back-")));

        verify(r2Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        Postcard updated = postcardRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getFrontImageUrl()).startsWith("https://pub-test.r2.dev/postcards/");
        assertThat(updated.getBackImageUrl()).startsWith("https://pub-test.r2.dev/postcards/");
    }

    @Test
    void uploadImages_onlyFrontProvided_leavesBackUnchanged() throws Exception {
        Postcard saved = saveSamplePostcard();
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", samplePngBytes(200, 100));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backImageUrl").value("https://example.com/old-back.jpg"));

        verify(r2Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadImages_widerThanMax_isResizedTo1600pxWide() throws Exception {
        Postcard saved = saveSamplePostcard();
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        when(r2Client.putObject(any(PutObjectRequest.class), bodyCaptor.capture()))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile front = new MockMultipartFile("front", "wide.png", "image/png", samplePngBytes(2000, 1000));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isOk());

        byte[] uploaded = bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes();
        BufferedImage resized = ImageIO.read(new ByteArrayInputStream(uploaded));
        assertThat(resized.getWidth()).isEqualTo(1600);
        assertThat(resized.getHeight()).isEqualTo(800);
    }

    @Test
    void uploadImages_bakesWatermarkIntoUploadedBytes() throws Exception {
        Postcard saved = saveSamplePostcard();
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        when(r2Client.putObject(any(PutObjectRequest.class), bodyCaptor.capture()))
                .thenReturn(PutObjectResponse.builder().build());

        int width = 400;
        int height = 300;
        MockMultipartFile front =
                new MockMultipartFile("front", "front.png", "image/png", samplePngBytes(width, height));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isOk());

        byte[] uploaded = bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(uploaded));
        int originalRgb = Color.BLUE.getRGB();

        long changedPixels = 0;
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                if (result.getRGB(x, y) != originalRgb) {
                    changedPixels++;
                }
            }
        }

        long totalPixels = (long) result.getWidth() * result.getHeight();
        double changedFraction = changedPixels / (double) totalPixels;
        assertThat(changedFraction).isGreaterThan(0.01);
    }

    @Test
    void uploadImages_replacingExistingR2Image_deletesOldObjectBeforeStoringNew() throws Exception {
        Postcard saved = savePostcardWithR2Images();
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", samplePngBytes(200, 100));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isOk());

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(r2Client, times(1)).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().key()).isEqualTo(EXISTING_FRONT_KEY);
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("test-bucket");
        verify(r2Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        InOrder order = inOrder(r2Client);
        order.verify(r2Client).deleteObject(any(DeleteObjectRequest.class));
        order.verify(r2Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        Postcard updated = postcardRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getFrontImageUrl())
                .startsWith("https://pub-test.r2.dev/postcards/" + saved.getId() + "/front-");
        assertThat(updated.getBackImageUrl()).isEqualTo("https://pub-test.r2.dev/" + EXISTING_BACK_KEY);
    }

    @Test
    void deletePostcard_withR2Images_deletesBothFromR2AndRemovesRecord() throws Exception {
        Postcard saved = savePostcardWithR2Images();

        mockMvc.perform(delete("/api/postcards/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(r2Client, times(2)).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues())
                .extracting(DeleteObjectRequest::key)
                .containsExactlyInAnyOrder(EXISTING_FRONT_KEY, EXISTING_BACK_KEY);
        assertThat(deleteCaptor.getAllValues()).allMatch(req -> req.bucket().equals("test-bucket"));

        assertThat(postcardRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletePostcard_withoutR2Images_doesNotCallR2DeleteButStillRemovesRecord() throws Exception {
        Postcard saved = saveSamplePostcard();

        mockMvc.perform(delete("/api/postcards/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isNoContent());

        verifyNoInteractions(r2Client);
        assertThat(postcardRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletePostcard_missing_returns404AndDoesNotCallR2() throws Exception {
        mockMvc.perform(delete("/api/postcards/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(r2Client);
    }

    @Test
    void uploadImages_unsupportedContentType_returns400() throws Exception {
        Postcard saved = saveSamplePostcard();
        MockMultipartFile front = new MockMultipartFile("front", "front.gif", "image/gif", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(r2Client);
    }

    @Test
    void uploadImages_noFilesProvided_returns400() throws Exception {
        Postcard saved = saveSamplePostcard();

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(r2Client);
    }

    @Test
    void uploadImages_noToken_returns401() throws Exception {
        Postcard saved = saveSamplePostcard();
        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", samplePngBytes(50, 50));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId()).file(front))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(r2Client);
    }

    @Test
    void uploadImages_invalidToken_returns401() throws Exception {
        Postcard saved = saveSamplePostcard();
        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", samplePngBytes(50, 50));

        mockMvc.perform(multipart("/api/postcards/{id}/images", saved.getId())
                        .file(front)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(r2Client);
    }
}
