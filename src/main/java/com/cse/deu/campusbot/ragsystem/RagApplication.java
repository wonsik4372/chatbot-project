/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cse.deu.campusbot.ragsystem;

import com.cse.deu.campusbot.api.ChatHandler;
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
        String ollamaUrl = ConfigReader.getProperty("ollama.base.url", "http://localhost:11434");
        String embedModelName = ConfigReader.getProperty("ollama.embedding.model", "nomic-embed-text");
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
        
        DataIngestionService ingestionService = new DataIngestionService(embeddingModel, store);
        RagChatService chatService = new RagChatService(chatModel, embeddingModel, store);
        ChatHandler chatHandler = new ChatHandler(chatService);

        // 만약 서버 켤 때마다 특정 폴더를 자동 학습 
        // try {
        //     ingestionService.indexDocuments("data/docs");
        // } catch (Exception e) {
        //     System.err.println("자동 학습 실패: " + e.getMessage());
        // }

        // ==========================================
        // 4. Javalin 웹 서버 구동 및 라우팅
        // ==========================================
        
        System.out.println(" 데이터 학습 프로세스 시작...");

        // 로컬 디렉토리 학습
        String docDir = ConfigReader.getProperty("ingestion.directory", "");
        if (!docDir.isBlank()) {
            try {
                int fileChunks = ingestionService.indexDocuments(docDir);
                System.out.println("📂 문서 학습 완료: " + fileChunks + "개 청크 생성됨.");
            } catch (Exception e) {
                System.err.println("❌ 문서 학습 실패 (" + docDir + "): " + e.getMessage());
            }
        }

        // 웹 URL 리스트 학습
        String urlList = ConfigReader.getProperty("ingestion.urls", "");
        if (!urlList.isBlank()) {
            for (String url : urlList.split(",")) {
                String targetUrl = url.trim();
                if (!targetUrl.isEmpty()) {
                    try {
                        int urlChunks = ingestionService.crawlAndIndexUrl(targetUrl, 1);
                        System.out.println("🌐 URL 학습 완료 (" + targetUrl + "): " + urlChunks + "개 청크 생성됨.");
                    } catch (Exception e) {
                        System.err.println("❌ URL 학습 실패 (" + targetUrl + "): " + e.getMessage());
                    }
                }
            }
        }
        
        Javalin app = Javalin.create(config -> {
            // 프론트엔드 파일(index.html 등)을 제공할 정적 폴더 경로 지정
            config.staticFiles.add("/public", Location.CLASSPATH);
            
            // CORS 에러 방지 (로컬에서 프론트/백엔드 통신 허용) - ✨ 수정된 부분
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
        }).start(port);

        // 프론트엔드에서 POST 요청으로 질문이 오면 ChatHandler로 연결
        app.post("/api/chat", chatHandler::handleChat);

        System.out.println("서버 구동 완료! 브라우저에서 http://localhost:" + port + " 에 접속해 보세요.");
    }
}
