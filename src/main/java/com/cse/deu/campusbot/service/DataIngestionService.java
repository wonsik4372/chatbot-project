/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.service;

import com.cse.deu.campusbot.util.ConfigReader;
import com.cse.deu.campusbot.parser.MarkdownCsvParser;
import com.cse.deu.campusbot.parser.TabulaPdfParser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 문서 분할 및 청킹, 벡터 DB 저장
 * @author wonsik
 */
public class DataIngestionService {
    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final RagChatService compiler; 
    private final Set<String> visitedUrls = new HashSet<>();
    
    // 글자 수 기반 청크 사이즈 설정
    private static final int CHUNK_SIZE = 1000;
    // 15% 오버랩 설정
    private static final int OVERLAP_SIZE = 150;
    
    private static final String HISTORY_FILE = "data/ingestion_history.properties";
    private final Properties ingestionHistory = new Properties();
    
    public DataIngestionService(EmbeddingModel embeddingModel, 
                                InMemoryEmbeddingStore<TextSegment> embeddingStore,
                                RagChatService compiler) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.compiler = compiler;
        
        loadHistory();
    }
    
    private void loadHistory() {
        try {
            File historyFile = new File(HISTORY_FILE);
            if (historyFile.exists()) {
                try (FileInputStream fis = new FileInputStream(historyFile)) {
                    ingestionHistory.load(fis);
                }
            } else {
                File parent = historyFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                historyFile.createNewFile();
            }
        } catch (Exception e) {
            System.err.println("⚠️ 학습 이력 파일 로드 실패: " + e.getMessage());
        }
    }

    private void saveHistory() {
        try (FileOutputStream fos = new FileOutputStream(HISTORY_FILE)) {
            ingestionHistory.store(fos, "Document Ingestion History (Path -> LastModified)");
        } catch (Exception e) {
            System.err.println("⚠️ 학습 이력 파일 저장 실패: " + e.getMessage());
        }
    }
    
    // ========================================================================
    // 원본 문서(PDF, CSV)를 마크다운으로 추출하여 parsingMD 폴더에 저장하는 전처리 메서드
    // ========================================================================
    public void convertRawDocumentsToMarkdown(String directoryPath) {
        System.out.println("📄 [1단계] 원본 문서(PDF, CSV) 마크다운 변환을 시작합니다: " + directoryPath);
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) return;

        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            List<File> filesToConvert = paths
                    .filter(Files::isRegularFile)
                    // 변환 타겟은 오직 pdf와 csv
                    .filter(path -> path.toString().toLowerCase().matches(".*\\.(pdf|csv)$"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            for (File file : filesToConvert) {
                try {
                    // PDF/CSV에서 텍스트를 추출
                    String extractedText = extractText(file);
                    
                    if (!extractedText.isEmpty()) {
                        // 추출된 텍스트를 parsingMD 폴더에 .md 파일로 저장
                        saveParsedTextForDebug(file.getName(), extractedText);
                    }
                } catch (Exception e) {
                    System.err.println("❌ 문서 파싱 실패 [" + file.getName() + "]: " + e.getMessage());
                }
            }
            System.out.println("✅ [1단계 완료] PDF/CSV -> MD 변환이 완료되었습니다.");
        } catch (Exception e) {
            System.err.println("❌ 디렉토리 탐색 실패: " + e.getMessage());
        }
    }
    
    // ========================================================================
    // 디렉토리 문서 학습 (REST API 대응을 위해 추가된 청크 개수 반환)
    // ========================================================================
    public int indexDocuments(String directoryPath) throws Exception {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("ERROR: 유효한 디렉토리가 아닙니다: " + directoryPath);
        }

        int totalChunksAdded = 0;
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            List<File> filesToProcess = paths
                    .filter(Files::isRegularFile)
                    // ✨ 원본 PDF, CSV는 제쳐두고 오직 가공된 md 파일(또는 캐시txt)만 인덱싱 타겟으로 잡습니다.
                    .filter(path -> path.toString().toLowerCase().matches(".*\\.md$"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            for (File file : filesToProcess) {
                String absolutePath = file.getAbsolutePath();
                String lastModified = String.valueOf(file.lastModified());
                String savedModified = ingestionHistory.getProperty(absolutePath);

                if (lastModified.equals(savedModified)) {
                    System.out.println("⏭️ 스킵됨 (변경사항 없음): " + file.getName());
                    continue;
                }

                // 파일 안의 정제된 마크다운 텍스트를 그대로 읽어옴
                String rawText = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (!rawText.trim().isEmpty()) {
                    // 이미 컴파일된 md 파일이므로 전처리 없이 온전히 청킹 혹은 통임베딩 처리
                    totalChunksAdded += processAndStoreText(rawText, file.getName(), true);

                    ingestionHistory.setProperty(absolutePath, lastModified);
                    saveHistory();
                }
            }
        }
        return totalChunksAdded;
        
    }

    // ========================================================================
    // 웹페이지 크롤링 및 LLM 기반 마크다운 컴파일
    // ========================================================================
    public int crawlUrl(String urlString, int maxPagesToCrawl) {
        System.out.println("🌐 [하위 메뉴 추적 크롤링] 탐색을 시작합니다...");

        Queue<String> urlQueue = new LinkedList<>();
        urlQueue.add(urlString);

        int crawledCount = 0;
        int chunksAdded = 0;

        String cacheDirPath = ConfigReader.getProperty("crawler.cache.dir", "data/parsingMD");
        File cacheDir = new File(cacheDirPath);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        while (!urlQueue.isEmpty() && crawledCount < maxPagesToCrawl) {
            String currentUrl = urlQueue.poll();
            
            // 1. URL 정규화 및 중복 체크
            String normalizedUrl = currentUrl.split("#")[0].replaceAll("/$", "");
            if (visitedUrls.contains(normalizedUrl)) continue;
            
            visitedUrls.add(normalizedUrl);
            crawledCount++;

            String safeFileName = normalizedUrl.replaceAll("[^a-zA-Z0-9]", "_") + ".md";
            File cacheFile = new File(cacheDir, safeFileName);

            // 2. 캐시 적중 시
            if (cacheFile.exists()) {
                System.out.println("⚡ [웹 캐시 적중] (" + crawledCount + ") " + normalizedUrl);
                try {
                    String cachedMarkdown = Files.readString(cacheFile.toPath());
                    org.jsoup.nodes.Document doc = Jsoup.parse(cachedMarkdown, normalizedUrl);
                    discoverLinks(doc, urlQueue, visitedUrls, urlString);
                    continue;
                } catch (IOException e) {
                    System.err.println("❌ 캐시 읽기 실패: " + e.getMessage());
                }
            } 
            
            // 3. 신규 페이지 크롤링
            try {
                System.out.println("📥 [신규 페이지 웹 수집 중] (" + crawledCount + "/" + maxPagesToCrawl + ") -> " + normalizedUrl);
                
                org.jsoup.nodes.Document doc = Jsoup.connect(normalizedUrl)
                        .timeout(10000)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .get();

                // 하위 링크 탐색
                discoverLinks(doc, urlQueue, visitedUrls, urlString);

                // 불필요한 요소 1차 제거 (Jsoup DOM 레벨)
                doc.select("nav, footer, .admin, #header, .sidebar, script, style, .footer_content").remove();
                
                String pageTitle = doc.title();
                String textContent = doc.body().text();

                if (textContent.trim().isEmpty()) continue;

                // 4. LLM을 이용한 RAG 최적화 마크다운 컴파일
                String structuredMarkdown = compiler.compileWebContent(textContent, "웹페이지: " + pageTitle);

                try (FileWriter writer = new FileWriter(cacheFile)) {
                    writer.write(structuredMarkdown);
                }

                Thread.sleep(2000);

            } catch (Exception e) {
                System.err.println("❌ 웹 페이지 수집 실패 [" + normalizedUrl + "]: " + e.getMessage());
            }
        }
        
        System.out.println("✅ [크롤링 완료] 총 반환된 청크 수: " + chunksAdded);
        return chunksAdded;
    }

    private void discoverLinks(org.jsoup.nodes.Document doc, Queue<String> urlQueue, Set<String> visitedUrls, String startUrl) {
        try {
            java.net.URL base = new java.net.URL(startUrl);
            String host = base.getHost();

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absUrl = link.attr("abs:href").trim();
                if (absUrl.isEmpty() || absUrl.contains("#")) continue;
                
                if (absUrl.contains(host) && absUrl.contains("/se/")) {
                    if (absUrl.contains("download.do") || absUrl.contains("bbsMode=view")) {
                        continue; 
                    }
                    
                    String normalizedAbsUrl = absUrl.replaceAll("/$", "");
                    
                    if (!visitedUrls.contains(normalizedAbsUrl) && !urlQueue.contains(normalizedAbsUrl)) {
                        urlQueue.add(normalizedAbsUrl);
                    }
                }
            }
        } catch (Exception e) {
            // 무시
        }
    }

    // ========================================================================
    // 공통 핵심 로직: 텍스트 전처리 -> 오버랩 청킹 -> 임베딩 -> JSON 저장
    // ========================================================================
    private int processAndStoreText(String rawText, String sourceName, boolean isPreCompiled) {
        String finalContent;
        List<TextSegment> segments;

        if (isPreCompiled) {
            // ✨ 1. 크롤링된 마크다운 캐시 파일인 경우 (.cache.txt)
            // LLM이 구조화한 들여쓰기와 줄바꿈이 망가지지 않도록 전처리(clean)를 아예 생략합니다.
            finalContent = rawText.trim();
            
            Document document = Document.from(finalContent);
            document.metadata().put("source", sourceName);
            
            // 텍스트를 난도질하지 않고, 전체를 온전히 하나의 청크로 묶어서 저장합니다.
            segments = Collections.singletonList(TextSegment.from(finalContent, document.metadata()));
            
        } else {
            // ✨ 2. 일반 문서 파일인 경우 (PDF, CSV, TXT 등)
            // 기존처럼 불필요한 다중 공백과 제어 문자를 압축하는 전처리를 수행합니다.
            finalContent = cleanAndPreprocessText(rawText);
            
            Document document = Document.from(finalContent);
            document.metadata().put("source", sourceName);
            
            // 설정된 사이즈(500)에 맞춰 청킹(분할)을 수행합니다.
            segments = DocumentSplitters
                    .recursive(CHUNK_SIZE, OVERLAP_SIZE)
                    .split(document);
        }

        // 벡터 임베딩 및 메모리 스토어 저장
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);

        // 물리적 파일(JSON)로 디스크에 직렬화하여 저장
        String storePath = ConfigReader.getProperty("rag.store.path", "data/rag-store.json");
        embeddingStore.serializeToFile(storePath);

        System.out.println("✅ [" + sourceName + "] 인덱싱 완료 -> " + segments.size() + " 청크 생성 및 저장");
        return segments.size();
    }

    private String cleanAndPreprocessText(String text) {
        String processed = text.replaceAll("[ \\t]+", " "); 
        processed = processed.replaceAll("(\\r?\\n\\s*){3,}", "\n\n"); 
        processed = processed.replaceAll("[\\u0000-\\u0008\\u000B-\\u000C\\u000E-\\u001F]", "");
        return processed.trim();
    }

    // ========================================================================
    // 문서 파일 텍스트 추출 (TXT, PDF, CSV)
    // ========================================================================
    private String extractText(File file) throws Exception {
        String rawFileName = file.getName();
        String contextName = rawFileName.contains(".") 
                             ? rawFileName.substring(0, rawFileName.lastIndexOf(".")) 
                             : rawFileName;

        if (rawFileName.toLowerCase().endsWith(".pdf")) {
            Document document = FileSystemDocumentLoader.loadDocument(file.toPath(), new TabulaPdfParser());
            InputStream csvStream = new ByteArrayInputStream(document.text().getBytes(StandardCharsets.UTF_8));
            Document finalDoc = new MarkdownCsvParser().parse(csvStream);
            return "[문서명:" + contextName + "]\n" + finalDoc.text();
            
        } else if (rawFileName.toLowerCase().endsWith(".csv")) {
            Document document = FileSystemDocumentLoader.loadDocument(file.toPath(), new MarkdownCsvParser());
            return "[문서명:" + contextName + "]\n" + document.text();
            
        } else if (rawFileName.toLowerCase().endsWith(".txt")) {
            return "[문서명:" + contextName + "]\n" + Files.readString(file.toPath());
        }
        
        return "";
    }
    
    private void saveParsedTextForDebug(String originalFileName, String parsedText) {
        File debugDir = new File("data/parsingMD");
        if (!debugDir.exists()) {
            debugDir.mkdirs(); 
        }

        String baseName = originalFileName;
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        File outputFile = new File(debugDir, baseName + ".md");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(parsedText);
            System.out.println("📝 [디버그] 파싱 결과 확인용 파일 생성: " + outputFile.getPath());
        } catch (IOException e) {
            System.err.println("⚠️ [디버그] 텍스트 저장 실패: " + e.getMessage());
        }
    }
}