package com.agentguard.rag;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * A chunk of text + its vector, for pgvector similarity search.
 * The vector is stored as text "[0.1,0.2,...]" and cast to vector on write;
 * reads cast back to text so no pgvector JDBC dependency is needed.
 * Dimension 768 = nomic-embed-text (Ollama) / text-embedding-004 (Gemini).
 */
@Entity
@Table(name = "embeddings")
public class Embedding {
  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private String source; // 'case' | 'policy' | 'tool_doc'

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @Column(nullable = false, columnDefinition = "text")
  private String chunk;

  @ColumnTransformer(write = "?::vector")
  @Column(columnDefinition = "vector(768)")
  private String embedding;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Embedding() {}

  public UUID getId() { return id; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public UUID getSourceId() { return sourceId; }
  public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
  public String getChunk() { return chunk; }
  public void setChunk(String chunk) { this.chunk = chunk; }
  public String getEmbedding() { return embedding; }
  public void setEmbedding(String embedding) { this.embedding = embedding; }
}
