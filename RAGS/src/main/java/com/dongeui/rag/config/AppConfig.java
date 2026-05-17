package com.dongeui.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;

import java.time.Duration;

public class AppConfig {

    public static final String OLLAMA_URL = "http://localhost:11434";

    public static final String CHAT_MODEL = "gemma3:12b";

    public static final String EMBEDDING_MODEL = "nomic-embed-text";

    public static final String JSON_DIR = "./json_cache";

    private final EmbeddingModel embeddingModel;

    private final ChatLanguageModel chatModel;
    
    public static final String CACHE_DIR =  "cache";
    
    public AppConfig() {

        embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(EMBEDDING_MODEL)
                .timeout(Duration.ofSeconds(300))
                .build();

        chatModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(CHAT_MODEL)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(300))
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