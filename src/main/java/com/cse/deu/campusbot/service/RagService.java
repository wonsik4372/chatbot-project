/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.service;


import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.nio.file.Paths;
import java.time.Duration;

@Service
public class RagService {

    private Assistant assistant;

    // 인터페이스는 그대로 유지
    interface Assistant {
        String ask(String message);
    }

    // 서버 시작 시 딱 한 번만 실행 (문서 인덱싱)
    @PostConstruct
    public void init() {
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("gemma4:latest")
                .timeout(Duration.ofSeconds(400))
                .build();

        OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("mxbai-embed-large")
                .build();

        InMemoryEmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // 문서 로드 (경로는 실제 프로젝트 내부 위치에 맞춰 수정)
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(800, 150))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

       // resources 아래의 docs 폴더를 읽어옵니다.
        String path = Paths.get("src/main/resources/docs").toAbsolutePath().toString();
        ingestor.ingest(FileSystemDocumentLoader.loadDocuments(path, new TextDocumentParser()));
        
        // Assistant 생성
        this.assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(3)
                        .minScore(0.6)
                        .build())
                .build();
    }

    public String askQuestion(String question) {
        return assistant.ask(question);
    }
}