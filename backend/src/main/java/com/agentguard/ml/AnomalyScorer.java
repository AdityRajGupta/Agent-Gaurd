package com.agentguard.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Client for the ml-service /score endpoint.
 * Returns empty on any failure — the caller fail-closes (high-sensitivity
 * tools require human approval when the scorer is unreachable).
 */
@Component
public class AnomalyScorer {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;
  private final Duration timeout;

  public AnomalyScorer(
      @Value("${agentguard.ml-service-url:http://localhost:8001}") String baseUrl,
      @Value("${agentguard.anomaly.scorer-timeout-ms:3000}") long timeoutMs) {
    this.baseUrl = baseUrl;
    this.timeout = Duration.ofMillis(timeoutMs);
  }

  public Optional<Double> score(UUID agentId, double[] features) {
    try {
      String body = json.writeValueAsString(
          Map.of("agent_id", agentId.toString(), "features", features));
      HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/score"))
          .timeout(timeout)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() / 100 != 2) return Optional.empty();
      double s = json.readTree(res.body()).path("anomaly_score").asDouble();
      return Optional.of(s);
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
