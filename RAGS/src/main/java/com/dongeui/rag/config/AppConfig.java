package com.dongeui.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import java.time.Duration;

public class AppConfig {
    public static final String OLLAMA_URL = "http://localhost:11434";
    public static final String EMBEDDING_MODEL = "nomic-embed-text"; 
    public static final String CHAT_MODEL = "gemma4"; // 사용 중이신 로컬 모델명
    public static final String CACHE_DIR = "./docs_cache";

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;

    public AppConfig() {
        // EmbeddingModel: 대용량 지식 적재 시 부하 고려하여 타임아웃 5분 유지
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(EMBEDDING_MODEL)
                .timeout(Duration.ofSeconds(300))
                .build();

        // ChatModel: 무한 루프 차단 및 시간표 컴파일 성능을 가동하기 위한 최적 튜닝 버전
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(CHAT_MODEL)
                .temperature(0.0)       // 💡 완벽한 팩트 기반 출력을 위해 0.0 유지
                .repeatPenalty(1.1)     // 💡 [추가] 특정 문구 무한 반복(루프) 현상 방지 불도저 옵션
                .numCtx(4096)           // 💡 [추가] 로컬 모델 기억력(컨텍스트 창)을 4096으로 확장하여 대용량 표 처리 안정성 확보
                .numPredict(3072)       // 💡 [추가] 마크다운 출력 도중 끊김 방지를 위해 생성 토큰 제한 확장
                .timeout(Duration.ofSeconds(300)) // 💡 로컬 인프라 생성 속도를 고려한 5분 타임아웃
                .build();
    }

    public EmbeddingModel getEmbeddingModel() {
        // 동의대 컴퓨터소프트웨어공학과 RAG 시스템의 벡터 인덱싱 코어
        return embeddingModel;
    }

    public ChatLanguageModel getChatModel() {
        // 지식 가독성 마크다운 컴파일러 및 최종 답변 생성 코어
        return chatModel;
    }
}