package com.agentguard.ml;

import com.agentguard.events.ToolEvent;
import com.agentguard.events.ToolEventRepository;
import com.agentguard.events.ToolDefinitionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Sliding-window feature extraction per agent (Phase 3).
 * Window: last 10 minutes. Vector:
 *   [call_freq, rare_tool_rate, sensitive_access_count, ngram_novelty, tod_deviation]
 * Must match the columns produced by ml-service/generate_baseline.py.
 */
@Service
public class FeatureService {
  private static final Duration WINDOW = Duration.ofMinutes(10);

  private final ToolEventRepository events;
  private final ToolDefinitionRepository tools;

  public FeatureService(ToolEventRepository events, ToolDefinitionRepository tools) {
    this.events = events;
    this.tools = tools;
  }

  public double[] features(UUID agentId) {
    Instant since = Instant.now().minus(WINDOW);
    List<ToolEvent> window =
        events.findByAgentIdAndCreatedAtAfterOrderByCreatedAtAsc(agentId, since);
    List<ToolEvent> history = events.findByAgentIdOrderByCreatedAtDesc(agentId);
    // history older than the window, for "novelty vs baseline" features
    List<ToolEvent> baseline =
        history.stream().filter(e -> e.getCreatedAt().isBefore(since)).toList();

    double callFreq = window.size() / 10.0;

    double rareRate = 0.0;
    if (!window.isEmpty() && !baseline.isEmpty()) {
      Map<UUID, Long> counts = baseline.stream()
          .collect(Collectors.groupingBy(ToolEvent::getToolId, Collectors.counting()));
      Set<UUID> rare = counts.entrySet().stream()
          .filter(e -> (double) e.getValue() / baseline.size() < 0.05)
          .map(Map.Entry::getKey).collect(Collectors.toSet());
      rareRate = window.stream().filter(e -> rare.contains(e.getToolId())).count()
          / (double) window.size();
    }

    Map<UUID, String> sensitivity = tools.findAll().stream()
        .collect(Collectors.toMap(
            t -> t.getId(), t -> t.getDataSensitivity(), (a, b) -> a));
    long sensitive = window.stream()
        .filter(e -> "high".equals(sensitivity.get(e.getToolId()))).count();

    double novelty = 0.0;
    List<String> windowBigrams = bigrams(window);
    // No baseline history (new agent) -> nothing to be novel against.
    // Without this guard every bigram looks "novel" and benign agents get flagged.
    if (!windowBigrams.isEmpty() && !baseline.isEmpty()) {
      Set<String> baseBigrams = new HashSet<>(bigrams(baseline));
      novelty = windowBigrams.stream().filter(b -> !baseBigrams.contains(b)).count()
          / (double) windowBigrams.size();
    }

    double todDev = 0.0;
    if (!baseline.isEmpty()) {
      List<Integer> hours = baseline.stream()
          .map(e -> e.getCreatedAt().atZone(java.time.ZoneOffset.UTC).getHour())
          .sorted().toList();
      int median = hours.get(hours.size() / 2);
      int nowHour = Instant.now().atZone(java.time.ZoneOffset.UTC).getHour();
      todDev = Math.abs(nowHour - median) / 12.0;
    }

    return new double[]{callFreq, rareRate, (double) sensitive, novelty, todDev};
  }

  private static List<String> bigrams(List<ToolEvent> evts) {
    List<String> out = new ArrayList<>();
    for (int i = 1; i < evts.size(); i++) {
      out.add(evts.get(i - 1).getToolId() + ">" + evts.get(i).getToolId());
    }
    return out;
  }
}
