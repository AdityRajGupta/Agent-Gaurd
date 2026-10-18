package com.agentguard.apikeys;

import com.agentguard.security.Hashing;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * API keys are shown ONCE at issuance. Only the SHA-256 hash is stored,
 * so a database leak does not expose usable keys.
 */
@Service
public class AgentApiKeyService {
  private final AgentApiKeyRepository repo;
  private final SecureRandom random = new SecureRandom();

  public AgentApiKeyService(AgentApiKeyRepository repo) {
    this.repo = repo;
  }

  public record IssuedKey(UUID keyId, String rawKey) {}

  public IssuedKey issue(UUID agentId, String label) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = HexFormat.of().formatHex(bytes); // 64 hex chars
    AgentApiKey k = new AgentApiKey();
    k.setAgentId(agentId);
    k.setKeyHash(Hashing.sha256Hex(raw));
    k.setLabel(label == null ? "default" : label);
    repo.save(k);
    return new IssuedKey(k.getId(), raw);
  }

  /** Resolve a raw API key to its agent. Empty if unknown or revoked. */
  public Optional<UUID> resolveAgent(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) return Optional.empty();
    String hash = Hashing.sha256Hex(rawKey);
    return repo.findByRevokedFalse().stream()
        .filter(k -> k.getKeyHash().equals(hash))
        .map(AgentApiKey::getAgentId)
        .findFirst();
  }

  public void revoke(UUID keyId) {
    repo.findById(keyId).ifPresent(k -> {
      k.setRevoked(true);
      repo.save(k);
    });
  }
}
