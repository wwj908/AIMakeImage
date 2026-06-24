package com.makeimage.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ImageRenderService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(8);
    private static final int MAX_ATTEMPTS = 2;

    private final ObjectMapper objectMapper;
    private final OpenAiProviderService openAiProviderService;
    private final HttpClient httpClient;

    public ImageRenderService(ObjectMapper objectMapper, OpenAiProviderService openAiProviderService) {
        this.objectMapper = objectMapper;
        this.openAiProviderService = openAiProviderService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void render(String prompt, String negativePrompt, String mode, Path output, MultipartFile sourceImage) throws IOException {
        OpenAiProviderService.ProviderConfig provider = openAiProviderService.activeProvider()
                .orElseThrow(() -> new IllegalStateException("OpenAI 图片生成渠道未启用，请在管理后台添加并启用渠道"));
        try {
            if ("edit".equals(mode) && sourceImage != null && !sourceImage.isEmpty()) {
                retryRender(() -> renderWithImageEdit(provider, prompt, negativePrompt, output, sourceImage));
            } else {
                retryRender(() -> renderWithImageGeneration(provider, prompt, negativePrompt, output));
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI 图片生成请求被中断");
        } catch (HttpTimeoutException exception) {
            throw new IllegalStateException("OpenAI 图片生成超时，请稍后重试或降低图片尺寸");
        } catch (Exception exception) {
            if (isConnectionClosed(exception)) {
                throw new IllegalStateException("OpenAI 图片生成连接中断，请稍后重试");
            }
            throw new IllegalStateException("OpenAI 图片生成失败: " + exception.getMessage(), exception);
        }
    }

    private void retryRender(RenderCall call) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                call.run();
                return;
            } catch (Exception exception) {
                lastException = exception;
                if (attempt >= MAX_ATTEMPTS || !isRetryable(exception)) {
                    throw exception;
                }
                Thread.sleep(900L * attempt);
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }

    private boolean isRetryable(Exception exception) {
        return exception instanceof HttpTimeoutException || isConnectionClosed(exception);
    }

    private boolean isConnectionClosed(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("eof") || lower.contains("connection reset") || lower.contains("closed")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void renderWithImageGeneration(OpenAiProviderService.ProviderConfig provider, String prompt, String negativePrompt, Path output)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", provider.model());
        payload.put("prompt", buildPrompt(prompt, negativePrompt, "generate"));
        payload.put("size", detectSizeFromPrompt(prompt));
        payload.put("response_format", "b64_json");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(provider.baseUrl()) + "/v1/images/generations"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + provider.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        writeImageResponse(response, output);
    }

    private void renderWithImageEdit(OpenAiProviderService.ProviderConfig provider, String prompt, String negativePrompt, Path output, MultipartFile sourceImage)
            throws IOException, InterruptedException {
        String boundary = "----MakeImageBoundary" + System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(provider.baseUrl()) + "/v1/images/edits"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + provider.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(
                        boundary,
                        provider.model(),
                        buildPrompt(prompt, negativePrompt, "edit"),
                        detectSizeFromPrompt(prompt),
                        sourceImage
                )))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        writeImageResponse(response, output);
    }

    private byte[] buildMultipartBody(String boundary, String model, String prompt, String size, MultipartFile sourceImage)
            throws IOException {
        String lineBreak = "\r\n";
        String filename = sourceImage.getOriginalFilename() == null || sourceImage.getOriginalFilename().isBlank()
                ? "reference.png"
                : sourceImage.getOriginalFilename();
        String contentType = sourceImage.getContentType() == null || sourceImage.getContentType().isBlank()
                ? "image/png"
                : sourceImage.getContentType();

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeFormField(body, boundary, "model", model, lineBreak);
        writeFormField(body, boundary, "prompt", prompt, lineBreak);
        writeFormField(body, boundary, "size", size, lineBreak);
        writeFormField(body, boundary, "response_format", "b64_json", lineBreak);

        body.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"" + lineBreak).getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        body.write(sourceImage.getBytes());
        body.write(lineBreak.getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private void writeFormField(ByteArrayOutputStream body, String boundary, String name, String value, String lineBreak)
            throws IOException {
        body.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.write(lineBreak.getBytes(StandardCharsets.UTF_8));
    }

    private String detectSizeFromPrompt(String prompt) {
        if (containsAny(prompt, "9:16", "1:2", "1:3", "2:3", "3:4", "4:5")) {
            return "1024x1536";
        }
        if (containsAny(prompt, "16:9", "21:9", "2:1", "3:1", "3:2", "4:3", "5:4")) {
            return "1536x1024";
        }
        return "1024x1024";
    }

    private boolean containsAny(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String buildPrompt(String prompt, String negativePrompt, String mode) {
        StringBuilder builder = new StringBuilder();
        builder.append("请");
        builder.append("edit".equals(mode) ? "编辑参考图，保持主体自然合理。" : "生成一张高质量图片。");
        builder.append(" 需求：").append(prompt);
        if (negativePrompt != null && !negativePrompt.isBlank()) {
            builder.append(" 避免：").append(negativePrompt);
        }
        return builder.toString();
    }

    private void writeImageResponse(HttpResponse<String> response, Path output) throws IOException {
        if (response.body() == null || response.body().isBlank()) {
            throw new IllegalStateException("OpenAI 图片接口返回空响应");
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(extractErrorMessage(root, response.body()));
        }

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("图片接口未返回 data");
        }

        String base64Image = data.get(0).path("b64_json").asText();
        if (base64Image == null || base64Image.isBlank()) {
            throw new IllegalStateException("图片接口未返回 b64_json");
        }

        Files.write(output, Base64.getDecoder().decode(base64Image));
    }

    private String extractErrorMessage(JsonNode root, String fallback) {
        JsonNode error = root.path("error");
        String message = error.path("message").asText();
        if (!message.isBlank()) {
            return message;
        }
        String topLevelMessage = root.path("message").asText();
        if (!topLevelMessage.isBlank()) {
            return topLevelMessage;
        }
        return fallback == null || fallback.isBlank() ? "OpenAI 图片接口请求失败" : fallback;
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @FunctionalInterface
    private interface RenderCall {
        void run() throws Exception;
    }
}
