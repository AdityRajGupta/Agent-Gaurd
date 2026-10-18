package com.agentguard.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM provider chain (Phase 4). All providers are FREE:
 *   auto (default) — prefer GROQ/GEMINI free-tier keys when set, else local Ollama
 *   ollama         — local, no key, works offline
 *   groq           — free tier, Llama 3.3 70B
 *   gemini         — free tier, Gemini Flash
 *
 * If the configured provider has no key or is unreachable, and it isn't
 * ollama, we fall back to local Ollama automatically. Nothing here executes
 * anything — the LLM only drafts text; deterministic code validates it.
 */
@Component
public class LlmClient {
  public record LlmResult(String text, String provider) {}

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();

  private final String provider;
  private final String ollamaUrl, ollamaModel;
  private final String groqKey, groqModel;
  private final String geminiKey, geminiModel;

  public LlmClient(
      @Value("${agentguard.llm.provider:auto}") String provider,
      @Value("${agentguard.ollama.url:http://localhost:11434}") String ollamaUrl,
      @Value("${agentguard.ollama.model:llama3.1:8b}") String ollamaModel,
      @Value("${agentguard.groq.api-key:}") String groqKey,
      @Value("${agentguard.groq.model:llama-3.3-70b-versatile}") String groqModel,
      @Value("${agentguard.gemini.api-key:}") String geminiKey,
      @Value("${agentguard.gemini.model:gemini-2.0-flash}") String geminiModel) {
    this.provider = provider;
    this.ollamaUrl = ollamaUrl;
    this.ollamaModel = ollamaModel;
    this.groqKey = groqKey;
    this.groqModel = groqModel;
    this.geminiKey = geminiKey;
    this.geminiModel = geminiModel;
  }

  public LlmResult chat(String systemPrompt, String userPrompt) {
    String p = provider.toLowerCase();
    boolean auto = "auto".equals(p);

    // "auto" (default): prefer free-tier cloud keys when present, else local Ollama.
    // An explicit provider fails loudly on misconfiguration instead of silently
    // falling back — except "auto", which always has Ollama as the last resort.
    if ("groq".equals(p) || auto) {
      if (!groqKey.isBlank()) {
        try {
          return new LlmResult(compatibleChat(
              "https://api.groq.com/openai/v1/chat/completions", groqKey, groqModel,
              systemPrompt, userPrompt), "groq:" + groqModel);
        } catch (Exception e) {
          if (!auto) throw new IllegalStateException("groq failed: " + e.getMessage(), e);
        }
      } else if (!auto) {
        throw new IllegalStateException("LLM provider=groq but GROQ_API_KEY is not set");
      }
    }
    if ("gemini".equals(p) || auto) {
      if (!geminiKey.isBlank()) {
        try {
          return new LlmResult(geminiChat(systemPrompt, userPrompt), "gemini:" + geminiModel);
        } catch (Exception e) {
          if (!auto) throw new IllegalStateException("gemini failed: " + e.getMessage(), e);
        }
      } else if (!auto) {
        throw new IllegalStateException("LLM provider=gemini but GEMINI_API_KEY is not set");
      }
    }
    if ("openai".equals(p)) {
      throw new IllegalStateException(
          "LLM provider=openai is disabled; use Ollama or a free Groq/Gemini tier");
    }
    // Default / last resort: local Ollama. Free forever, works offline.
    return new LlmResult(ollamaChat(systemPrompt, userPrompt), "ollama:" + ollamaModel);
  }

  // ---------------------------------------------------------------- providers

  private String ollamaChat(String system, String user) {
    try {
      String body = json.writeValueAsString(Map.of(
          "model", ollamaModel, "stream", false,
          "options", Map.of("temperature", 0.2),
          "messages", List.of(
              Map.of("role", "system", "content", system),
              Map.of("role", "user", "content", user))));
      JsonNode root = post(ollamaUrl + "/api/chat", body, null);
      return root.path("message").path("content").asText();
    } catch (Exception e) {
      throw new IllegalStateException(
          "ollama unreachable at " + ollamaUrl
              + " (is `ollama serve` running with model " + ollamaModel + "?)", e);
    }
  }

  private String compatibleChat(String url, String key, String model,
      String system, String user) throws Exception {
    String body = json.writeValueAsString(Map.of(
        "model", model, "temperature", 0.2,
        "messages", List.of(
            Map.of("role", "system", "content", system),
            Map.of("role", "user", "content", user))));
    JsonNode root = post(url, body, key);
    String text = root.path("choices").path(0).path("message").path("content").asText();
    if (text.isBlank()) throw new IllegalStateException("empty completion");
    return text;
  }

  private String geminiChat(String system, String user) throws Exception {
    String url = "https://generativelanguage.googleapis.com/v1beta/models/"
        + geminiModel + ":generateContent?key=" + geminiKey;
    String body = json.writeValueAsString(Map.of(
        "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
        "generationConfig", Map.of("temperature", 0.2),
        "contents", List.of(Map.of("parts", List.of(Map.of("text", user))))));
    JsonNode root = post(url, body, null);
    String text = root.path("candidates").path(0).path("content")
        .path("parts").path(0).path("text").asText();
    if (text.isBlank()) throw new IllegalStateException("empty completion");
    return text;
  }

  private JsonNode post(String url, String body, String bearerKey) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(120))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearerKey != null && !bearerKey.isBlank()) {
      builder.header("Authorization", "Bearer " + bearerKey);
    }
    HttpResponse<String> res = http.send(builder.build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      throw new IllegalStateException("HTTP " + res.statusCode() + ": "
          + res.body().substring(0, Math.min(300, res.body().length())));
    }
    return json.readTree(res.body());
  }
}
