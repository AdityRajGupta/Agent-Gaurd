package com.agentguard.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Forwards ALLOWED tool calls to the MCP tool server (Phase 2 transport: HTTP).
 * Failures never bypass policy — the event is already recorded as ALLOW, and
 * the error is returned as the tool result for visibility.
 */
@Component
public class McpClient {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;
  private final String internalToken;

  public McpClient(@Value("${mcp.server.url:http://localhost:8002}") String baseUrl,
      @Value("${mcp.server.token:}") String internalToken) {
    this.baseUrl = baseUrl;
    this.internalToken = internalToken;
  }

  public String invoke(String toolName, Map<String, Object> args) {
    try {
      String body = json.writeValueAsString(Map.of("tool", toolName, "args", args));
      HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/invoke"))
          .timeout(Duration.ofSeconds(15))
          .header("Content-Type", "application/json")
          .header("X-AgentGuard-Internal-Token", internalToken)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> res =
          http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() / 100 != 2) {
        return "mcp error " + res.statusCode() + ": " + res.body();
      }
      return json.readTree(res.body()).path("result").asText();
    } catch (Exception e) {
      return "mcp unreachable: " + e.getMessage();
    }
  }
}
