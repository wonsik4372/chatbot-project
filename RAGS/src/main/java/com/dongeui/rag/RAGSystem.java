package com.dongeui.rag;

import com.dongeui.rag.config.AppConfig;
import com.dongeui.rag.crawler.WebCrawler;
import com.dongeui.rag.llm.KnowledgeCompiler;
import com.dongeui.rag.parser.DocumentParser;
import com.dongeui.rag.repository.VectorKnowledgeStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

public class RAGSystem {

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // 인코딩 예외 무시
        }

        AppConfig config = new AppConfig();
        KnowledgeCompiler compiler = new KnowledgeCompiler(config.getChatModel());
        VectorKnowledgeStore store = new VectorKnowledgeStore(config.getEmbeddingModel(), config.getChatModel());
        DocumentParser parser = new DocumentParser();
        WebCrawler crawler = new WebCrawler(compiler, store);

        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("🌐 컴퓨터소프트웨어공학과 데이터 인덱싱 파이프라인 가동...");
            // 1. 웹 크롤링 수행 (웹 문서들이 캐시 폴더에 파일로 빌드됨)
            crawler.crawlAndIndexWeb("https://deuhome.deu.ac.kr/se/index.do");
            
            // ------------------------------------------------------------------
            // 🎯 [교정 조치 1] ./docs 폴더 내의 로컬 문서(PDF/CSV 등) 파이프라인 처리
            // ------------------------------------------------------------------
            String docPath = "./docs";
            File dir = new File(docPath);
            if (dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName().toLowerCase();
                        
                        String roomMetadata = "none";
                        String professorMetadata = "none";
                        if (file.getName().matches(".*\\d{3}.*")) {
                            roomMetadata = file.getName().replaceAll("[^0-9]", "");
                        } else if (name.endsWith(".pdf")) {
                            professorMetadata = file.getName().replace(".pdf", "").replace("교수님", "").trim();
                        }

                        File rawCsvFile = new File(AppConfig.CACHE_DIR, file.getName() + ".raw.csv");
                        File cacheMarkdownFile = new File(AppConfig.CACHE_DIR, file.getName() + ".cache.txt");

                        if (cacheMarkdownFile.exists()) {
                            System.out.println("⚡ [문서 캐시 적중] '" + file.getName() + "' 가공 지식을 로드합니다.");
                            String cachedMarkdown = Files.readString(cacheMarkdownFile.toPath(), StandardCharsets.UTF_8);
                            store.saveToVectorStore(cachedMarkdown, file.getName(), roomMetadata, professorMetadata);
                            continue;
                        }

                        System.out.println("📄 [신규 문서 발견] 2-Step 파이프라인 구동: " + file.getName());
                      
                        try {
                            String structuredMarkdown = "";

                            if (name.endsWith(".pdf")) {
                                String pureCsv = parser.extractPdfToPureCsv(file);
                                if (pureCsv.trim().isEmpty()) continue;
                                Files.writeString(rawCsvFile.toPath(), pureCsv, StandardCharsets.UTF_8);
                                System.out.println("💾 [1단계 성공] 메타데이터 일체형 격자 CSV 저장 완료 -> " + rawCsvFile.getName());

                                String lineStream = parser.parseFile(file); 
                                structuredMarkdown = compiler.compileTimetable(lineStream, file.getName());
                            } else {
                                String extractedContent = parser.parseFile(file);
                                if (extractedContent.trim().isEmpty()) continue;
                                structuredMarkdown = compiler.compileWebContent(extractedContent, file.getName());
                            }

                            try (FileWriter writer = new FileWriter(cacheMarkdownFile, StandardCharsets.UTF_8)) {
                                writer.write(structuredMarkdown);
                            }
                            System.out.println("💾 [2단계 성공] RAG 정형 마크다운 캐시 빌드 완료 -> " + cacheMarkdownFile.getName());

                            store.saveToVectorStore(structuredMarkdown, file.getName(), roomMetadata, professorMetadata);
                            
                        } catch (Exception e) {
                            System.err.println("❌ 파일 처리 중단 [" + file.getName() + "]: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }

            // ------------------------------------------------------------------
            // 🎯 [교정 조치 2] 캐시 폴더에 저장된 웹 크롤링 파일들(.cache.txt) 동기화 누락 문제 해결
            // ------------------------------------------------------------------
            System.out.println("\n🌐 [웹 캐시 동기화] 크롤러가 수집한 학과 웹 지식 파일들을 벡터 스토어에 추가 주입합니다.");
            File cacheDir = new File(AppConfig.CACHE_DIR);
            File[] cachedWebFiles = cacheDir.listFiles((d, name) -> name.startsWith("https") && name.endsWith(".cache.txt"));
            
            if (cachedWebFiles != null) {
                for (File webFile : cachedWebFiles) {
                    try {
                        String webContent = Files.readString(webFile.toPath(), StandardCharsets.UTF_8);
                        if (webContent == null || webContent.trim().isEmpty()) continue;
                        
                        System.out.println("📦 [웹 지식 로드 완료] -> " + webFile.getName() + " (" + webContent.length() + "자)");
                        
                        // 웹 크롤링 본문 내용 전체를 벡터 저장소에 완벽히 로드
                        store.saveToVectorStore(webContent, webFile.getName(), "none", "none");
                        
                    } catch (IOException e) {
                        System.err.println("❌ 웹 캐시 파일 주입 실패 [" + webFile.getName() + "]: " + e.getMessage());
                    }
                }
            }
            System.out.println("✅ 모든 학과 웹 및 문서 데이터가 메모리 벡터 저장소에 동기화되었습니다.");

            // ------------------------------------------------------------------
            // 인터페이스 구동
            // ------------------------------------------------------------------
            System.out.println("\n🤖 RAG SYSTEM 인터페이스 오픈.");
            while (true) {
                System.out.println("\n[메뉴] 1: 질문하기 | 2: 종료");
                System.out.print("선택 > ");
                String menu = scanner.nextLine().trim();

                if (menu.equals("2") || menu.equalsIgnoreCase("/bye")) {
                    System.out.println("시스템을 종료합니다.");
                    break;
                } else if (menu.equals("1")) {
                    System.out.print("\n🙋 질문을 입력하세요 > ");
                    String query = scanner.nextLine();
                    if (query.trim().isEmpty()) continue;

                    String answer = store.askQuestion(query);
                    System.out.println("\n📚 답변 > " + answer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
}