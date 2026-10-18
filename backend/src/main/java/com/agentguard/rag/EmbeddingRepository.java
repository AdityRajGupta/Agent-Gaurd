package com.agentguard.rag;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmbeddingRepository extends JpaRepository<Embedding, UUID> {

  /** Cosine-similarity search. vec is "[0.1,0.2,...]" (768 dims). */
  @Query(value = """
      SELECT id, source, source_id, chunk, embedding::text AS embedding, created_at
      FROM embeddings
      WHERE source = :source
      ORDER BY embedding <=> CAST(:vec AS vector)
      LIMIT :limit
      """, nativeQuery = true)
  List<Embedding> findSimilar(@Param("source") String source,
      @Param("vec") String vec, @Param("limit") int limit);
}
