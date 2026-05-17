/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cse.deu.campusbot.ragsystem;

import com.cse.deu.campusbot.api.ChatHandler;
import com.cse.deu.campusbot.model.ChatRequest;
import com.cse.deu.campusbot.model.ChatResponse;
import com.cse.deu.campusbot.service.DataIngestionService;
import com.cse.deu.campusbot.service.RagChatService;
import com.cse.deu.campusbot.util.ConfigReader;
import com.cse.deu.campusbot.util.EmbeddingStoreManager;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.time.Duration;

/**
 *
 * @author wonsik
 */
public class RagApplication {

    public static void main(String[] args) {
        System.out.println("🚀 CampusBot RAG 시스템 초기화를 시작합니다...");

        // ==========================================
        // 환경 설정 불러오기
        // ==========================================
        String ollamaUrl = ConfigReader.getProperty("ollama.base.url", "http://100.74.51.50:11434");
        String embedModelName = ConfigReader.getProperty("ollama.embedding.model", "bge-m3");
        String chatModelName = ConfigReader.getProperty("ollama.chat.model", "gemma4:latest");
        int port = ConfigReader.getIntProperty("server.port", 8080);

        // ==========================================
        // LangChain4j 모델 연결 설정
        // ==========================================
        
        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(embedModelName)
                .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(chatModelName)
                .temperature(0.0) // 사실 기반 답변을 위해 창의성(온도) 0으로 고정
                .timeout(Duration.ofMinutes(5))
                .build();

        // ==========================================
        // 의존성 조립 (Dependency Injection)
        // ==========================================
        InMemoryEmbeddingStore<TextSegment> store = EmbeddingStoreManager.getInstance();
        
        RagChatService chatService = new RagChatService(chatModel, embeddingModel, store);
        
        DataIngestionService ingestionService = new DataIngestionService(embeddingModel, store, chatService);
        ChatHandler chatHandler = new ChatHandler(chatService);

        // ==========================================
        // 데이터 학습 프로세스 시작
        // ==========================================
        System.out.println(" 데이터 학습 프로세스 시작...");
        // data/docs의 pdf,csv 파일들을 md 형식으로 변환하여 parsingMDdp 폴더에 저장 
        String docDir = ConfigReader.getProperty("ingestion.directory", "data/docs");
        ingestionService.convertRawDocumentsToMarkdown(docDir);
        
        // URL 크롤링 -> parsingMD vhfejdp 저장
        String urlList = ConfigReader.getProperty("ingestion.urls", "");
        if (!urlList.isBlank()) {
            for (String url : urlList.split(",")) {
                if (!url.trim().isEmpty()) {
                    ingestionService.crawlUrl(url.trim(), 30); 
                }
            }
        }

        // 인덱싱
        try {
            String cacheDirPath = ConfigReader.getProperty("crawler.cache.dir", "data/parsingMD");
            int chunks = ingestionService.indexDocuments(cacheDirPath); 
            System.out.println("🌐 통합 지식 베이스(MD) 학습 완료: " + chunks + "개 청크 생성됨.");
        } catch (Exception e) {
            System.err.println("❌ 문서 학습 실패: " + e.getMessage());
        }
        
        // ==========================================
        // Javalin 웹 서버 구동 및 라우팅
        // ==========================================
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
        }).start(port);

        // 프론트엔드에서 POST 요청으로 질문이 오면 ChatHandler로 연결
        app.post("/api/chat", ctx -> {
            ChatRequest req = ctx.bodyAsClass(ChatRequest.class);
            String realAnswer = chatService.askQuestion(req.getQuery()); 
            ctx.json(new ChatResponse(realAnswer));
        });

        System.out.println("서버 구동 완료! 브라우저에서 http://localhost:" + port + " 에 접속해 보세요.");
    }
}