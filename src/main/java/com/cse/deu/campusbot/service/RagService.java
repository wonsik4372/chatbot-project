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
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.service.SystemMessage;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;


@Service
public class RagService {

    private Assistant assistant;

    // 인터페이스는 그대로 유지
    public interface Assistant {
        @SystemMessage({
        "너는 동의대학교 컴퓨터소프트웨어공학과의 친절한 학과 챗봇이야.",
        "반드시 제공된 문서(Context)의 내용을 바탕으로 답변해야 해.",
         "문서에 없는 내용이라면 모른다고 답해줘.",   
    })
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
                .documentSplitter(DocumentSplitters.recursive(1000, 200))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

       // resources 아래의 docs 폴더를 읽어옵니다.
        try {
            // ClassPathResource를 사용하여 target 안이든 src 안이든 무조건 resources/docs를 찾아냅니다.
            ClassPathResource resource = new ClassPathResource("docs");
            Path path = Paths.get(resource.getURI());
            
            System.out.println("문서 로딩 경로 확인: " + path.toAbsolutePath().toString());
            ingestor.ingest(FileSystemDocumentLoader.loadDocuments(path, new TextDocumentParser()));
            
        } catch (IOException e) {
            System.err.println("🚨 docs 폴더를 읽어오는 중 에러가 발생했습니다!");
            e.printStackTrace();
        }
        
        
        // 1단계: 기존의 EmbeddingStore 기반 ContentRetriever 생성 (넉넉하게 15~20개 가져옴)
        ContentRetriever baseRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(20) // 리랭커에게 후보군을 많이 주기 위해 크게 설정
                .minScore(0.4)
                .build();
        
        // 2단계: 위에서 만든 baseRetriever를 리랭크 리트리버로 감싸기 (최종 3개만 엄선)
        ContentRetriever rerankerRetriever = new RerankContentRetriever(baseRetriever, 5);
        
        // 대답을 생성하는 메인 Assistant
        this.assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(rerankerRetriever) // 리랭커가 적용된 리트리버 주입!
                .build();
        
 
    }

    public String askQuestion(String question) {
        System.out.println("Orignal question: " + question);
        return assistant.ask(question);
    }
}