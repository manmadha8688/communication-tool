package com.cloudfuze.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** One JSON-mode chat completion. Throws on any failure; the caller decides what that means. */
@Component
public class OpenAiClient {

    private final RestClient client;
    private final String model;
    private final boolean configured;
    private final ObjectMapper json = new ObjectMapper();

    public OpenAiClient(@Value("${app.openai.api-key:}") String apiKey,
                        @Value("${app.openai.model:gpt-4o-mini}") String model,
                        @Value("${app.openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(15_000);
        f.setReadTimeout(90_000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(f)
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.model = model;
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    public boolean configured() {
        return configured;
    }

    public JsonNode completeJson(String system, String user) throws Exception {
        return completeJson(system, user, model);
    }

    /**
     * A short-lived secret the candidate's browser uses to open ONE live voice session with the given
     * settings. The real API key never leaves the server; the secret expires in about a minute if unused.
     */
    public JsonNode realtimeSecret(Map<String, Object> session) throws Exception {
        if (!configured) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }
        String raw = client.post().uri("/realtime/client_secrets").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("session", session)).retrieve().body(String.class);
        return json.readTree(raw);
    }

    /** The AI participant's line, spoken. MP3 bytes. */
    public byte[] speech(String text, String voice, String instructions) {
        if (!configured) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }
        Map<String, Object> body = Map.of("model", "gpt-4o-mini-tts", "voice", voice, "input", text,
                "response_format", "mp3", "instructions", instructions);
        return client.post().uri("/audio/speech").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(byte[].class);
    }

    /** What the candidate said, as text. Accepts the browser's webm/ogg/mp4 recording as it is. */
    public String transcribe(byte[] audio, String filename) throws Exception {
        if (!configured) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }
        org.springframework.util.LinkedMultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("model", "gpt-4o-mini-transcribe");
        form.add("language", "en");
        form.add("file", new org.springframework.core.io.ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        String raw = client.post().uri("/audio/transcriptions").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form).retrieve().body(String.class);
        return json.readTree(raw).path("text").asText("").trim();
    }

    /** Temperature 0 and a fixed seed: the same answer should get the same judgement. */
    public JsonNode completeJson(String system, String user, String useModel) throws Exception {
        return chat(system, List.of(Map.of("role", "user", "content", user)), useModel, 0.0);
    }

    /** A JSON-mode chat with a full message history, for the meeting role-play. */
    public JsonNode chat(String system, List<Map<String, String>> history, String useModel, double temperature) throws Exception {
        List<Map<String, String>> msgs = new java.util.ArrayList<>();
        msgs.add(Map.of("role", "system", "content", system));
        msgs.addAll(history);
        if (!configured) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }
        Map<String, Object> body = Map.of(
                "model", useModel == null || useModel.isBlank() ? model : useModel,
                "temperature", temperature,
                "seed", 7,
                "response_format", Map.of("type", "json_object"),
                "messages", msgs);
        String raw = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class);
        String content = json.readTree(raw).path("choices").path(0).path("message").path("content").asText();
        return json.readTree(content);
    }
}
