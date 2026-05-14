package com.mycompany.chatbot.rag.project;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 임베딩 생성 및 인메모리 벡터 스토어 관리
 *
 * - 임베딩 모델: Ollama all-minilm (로컬 Ollama 서버 사용) - 벡터 스토어: LangChain4j
 * InMemoryEmbeddingStore
 *
 * 운영 환경에서는 InMemoryEmbeddingStore를 Chroma / Qdrant / PgVector 등으로 교체하면 됩니다.
 */
public class VectorStoreManager {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final int TOP_K = 15;            // 검색 시 반환할 최대 청크 수
    private static final double MIN_SCORE = 0.20;         // 최소 유사도 임계값

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public VectorStoreManager() {
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(EMBEDDING_MODEL)
                .timeout(Duration.ofSeconds(60))
                .build();

        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    /**
     * 텍스트 세그먼트를 임베딩하여 벡터 스토어에 저장합니다.
     */
    public void addSegments(List<TextSegment> segments) {
        if (segments.isEmpty()) {
            return;
        }

        System.out.println("  [임베딩] " + segments.size() + "개 청크 임베딩 중...");

        // LangChain4j가 배치로 임베딩을 처리합니다
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        System.out.println("  [임베딩 완료] " + segments.size() + "개 벡터 저장됨.");
    }

    /**
     * 쿼리와 가장 유사한 청크를 검색하여 텍스트 목록으로 반환합니다.
     *
     * @param query 사용자 질문
     * @return 유사도 높은 청크 텍스트 목록
     */
    public List<EmbeddingMatch<TextSegment>> retrieveBySource(
            String query,
            java.util.function.Predicate<String> sourceFilter
    ) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<EmbeddingMatch<TextSegment>> matches
                = embeddingStore.findRelevant(queryEmbedding, 50, 0.0);

        matches = matches.stream()
                .filter(m -> {
                    String source
                            = m.embedded().metadata().getString("source");

                    return source != null && sourceFilter.test(source);
                })
                .limit(TOP_K)
                .toList();

        System.out.println("\n[디버그] 질문: " + query);

        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.printf(
                    "[디버그] %.4f | %s | %s...\n",
                    match.score(),
                    match.embedded().metadata().getString("source"),
                    match.embedded().text().substring(
                            0,
                            Math.min(50, match.embedded().text().length())
                    )
            );
        }

        return matches;
    }

    public List<EmbeddingMatch<TextSegment>> retrieve(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<EmbeddingMatch<TextSegment>> matches
                = embeddingStore.findRelevant(
                        queryEmbedding,
                        TOP_K,
                        MIN_SCORE
                );

        System.out.println("\n[디버그] 질문: " + query);

        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.printf(
                    "[디버그] %.4f | %s | %s...\n",
                    match.score(),
                    match.embedded().metadata().getString("source"),
                    match.embedded().text().substring(
                            0,
                            Math.min(50, match.embedded().text().length())
                    )
            );
        }

        return matches;
    }

    /**
     * 저장된 청크 수를 반환합니다. (InMemoryEmbeddingStore는 직접 size 지원 안 하므로 추적)
     */
    public int size() {
        // InMemoryEmbeddingStore는 size()를 직접 노출하지 않으므로
        // addSegments 호출 시 누적 카운트를 별도 관리하거나 
        // 여기서는 retrieve("test")로 확인하는 대신 외부에서 누적
        return -1; // 실제 운영 시 Qdrant/Chroma 스토어는 size()를 지원합니다
    }
}
