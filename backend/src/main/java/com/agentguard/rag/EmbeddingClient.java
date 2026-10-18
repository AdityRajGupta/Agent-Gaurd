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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Text embeddings for pgvector retrieval (Phase 4). Free providers:
 *   ollama (default) — nomic-embed-text, local, 768 dims
 *   gemini           — text-embedding-004 free tier, 768 dims
 * Returns empty when no provider is reachable; callers skip retrieval then.
 */
@Component
public class EmbeddingClient {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();

  private final String ollamaUrl, ollamaEmbedModel;
  private final String geminiKey, geminiEmbedModel;

  public EmbeddingClient(
      @Value("${agentguard.ollama.url:http://localhost:11434}") String ollamaUrl,
      @Value("${agentguard.ollama.embed-model:nomic-embed-text}") String ollamaEmbedModel,
      @Value("${agentguard.gemini.api-key:}") String geminiKey,
      @Value("${agentguard.gemini.embed-model:text-embedding-004}") String geminiEmbedModel) {
    this.ollamaUrl = ollamaUrl;
    this.ollamaEmbedModel = ollamaEmbedModel;
    this.geminiKey = geminiKey;
    this.geminiEmbedModel = geminiEmbedModel;
  }

  /** 768-dim vector, or empty if no embedding provider is reachable. */
  public Optional<double[]> embed(String text) {
    try {
      return Optional.of(ollamaEmbed(text));
    } catch (Exception e) {
      if (!geminiKey.isBlank()) {
        try {
          return Optional.of(geminiEmbed(text));
        } catch (Exception ignored) {
        }
      }
      return Optional.empty();
    }
  }

  public static String toVectorLiteral(double[] v) {
    return java.util.Arrays.stream(v).mapToObj(Double::toString)
        .collect(Collectors.joining(",", "[", "]"));
  }

  private double[] ollamaEmbed(String text) throws Exception {
    String body = json.writeValueAsString(
        Map.of("model", ollamaEmbedModel, "input", text));
    JsonNode root = post(ollamaUrl + "/api/embed", body, null);
    return toArray(root.path("embeddings").path(0));
  }

  private double[] geminiEmbed(String text) throws Exception {
    String url = "https://generativelanguage.googleapis.com/v1beta/models/"
        + geminiEmbedModel + ":embedContent?key=" + geminiKey;
    String body = json.writeValueAsString(Map.of(
        "content", Map.of("parts", List.of(Map.of("text", text)))));
    JsonNode root = post(url, body, null);
    return toArray(root.path("embedding").path("values"));
  }

  private static double[] toArray(JsonNode arr) {
    return StreamSupport.stream(arr.spliterator(), false)
        .mapToDouble(JsonNode::asDouble).toArray();
  }

  private JsonNode post(String url, String body, String bearerKey) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearerKey != null) builder.header("Authorization", "Bearer " + bearerKey);
    HttpResponse<String> res = http.send(builder.build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      throw new IllegalStateException("HTTP " + res.statusCode());
    }
    return json.readTree(res.body());
  }
}
