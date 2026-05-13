/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.service;

import com.cse.deu.campusbot.util.ConfigReader;
import com.cse.deu.campusbot.parser.MarkdownCsvParser;
import com.cse.deu.campusbot.parser.LocalPdfParser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
// import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

// import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 문서 분할 및 청킹, 벡터 DB 저장
 * @author wonsik
 */
public class DataIngestionService {
    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final Set<String> visitedUrls = new HashSet<>();
    
    // 글자 수 기반 청크 사이즈 설정
    private static final int CHUNK_SIZE = 500;
    // 5% 오버랩 설정
    private static final int OVERLAP_SIZE = 0;

    public DataIngestionService(EmbeddingModel embeddingModel, InMemoryEmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    // ========================================================================
    // 디렉토리 문서 학습 (REST API 대응을 위해 추가된 청크 개수 반환)
    // ========================================================================
    public int indexDocuments(String directoryPath) throws Exception {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("ERROR: 지정된 경로가 유효한 디렉토리가 아닙니다: " + directoryPath);
        }

        int totalChunksAdded = 0;
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            List<File> filesToProcess = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().matches(".*\\.(txt|pdf|csv)$"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            for (File file : filesToProcess) {
                String rawText = extractText(file);
                if (!rawText.trim().isEmpty()) {
                    totalChunksAdded += processAndStoreText(rawText, file.getName());
                }
            }
        }
        return totalChunksAdded;
    }

    // ========================================================================
    // 웹페이지 크롤링 학습
    // ========================================================================
    public int crawlAndIndexUrl(String urlString, int maxDepth) {
        // 1. URL 정규화 및 중복 체크
        String normalizedUrl = urlString.split("#")[0].replaceAll("/$", "");
        if (maxDepth < 0 || visitedUrls.contains(normalizedUrl)) return 0;

        visitedUrls.add(normalizedUrl);
        int chunksAdded = 0;

        try {
            System.out.println("🌐 크롤링 중: " + normalizedUrl + " (남은 깊이: " + maxDepth + ")");

            URL url = new URL(normalizedUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            String rawHtml = "";
            try (InputStream is = connection.getInputStream();
                 Scanner s = new Scanner(is, "UTF-8")) {
                s.useDelimiter("\\A");
                rawHtml = s.hasNext() ? s.next() : "";
            }

            // 현재 페이지 학습
            String cleanText = htmlToMarkdownText(rawHtml);
            if (!cleanText.isEmpty()) {
                chunksAdded += processAndStoreText(cleanText, "웹:" + normalizedUrl);
            }

            // 2. 하위 링크 탐색 및 재귀 호출
            if (maxDepth > 0) {
                // href="내용" 추출을 위한 정규식
                Matcher linkMatcher = Pattern.compile("(?is)<a[^>]+href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>").matcher(rawHtml);

                while (linkMatcher.find()) {
                    String href = linkMatcher.group(1).trim();
                    String nextUrl = resolveUrl(normalizedUrl, href); // 상대 경로를 절대 경로로 변환

                    // 학습 대상 필터링 (같은 도메인인지, 이미 방문했는지, 파일 링크가 아닌지)
                    if (nextUrl != null && isTargetUrl(nextUrl)) {
                        chunksAdded += crawlAndIndexUrl(nextUrl, maxDepth - 1);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ URL 처리 실패 (" + normalizedUrl + "): " + e.getMessage());
        }
        return chunksAdded;
    }

    /**
     * 상대 경로를 절대 경로로 변환하는 유틸리티
     */
    private String resolveUrl(String baseUrl, String href) {
        try {
            if (href.startsWith("javascript:") || href.startsWith("#") || href.isEmpty()) return null;
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, href); // 상대 경로 처리 핵심
            return resolved.toString().split("#")[0];
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 학습 대상인지 필터링 (불필요한 외부 사이트나 이미지 차단)
     */
    private boolean isTargetUrl(String url) {
        return url.contains("deu.ac.kr/se/") // 우리 학과 사이트 내부만 탐색
               && !url.matches(".*\\.(pdf|jpg|png|zip|css|js|docx|xlsx)$") // 문서/파일 제외
               && !visitedUrls.contains(url); // 중복 방지
    }

    // ========================================================================
    // 공통 핵심 로직: 텍스트 전처리 -> 오버랩 청킹 -> 임베딩 -> JSON 저장
    // ========================================================================
    private int processAndStoreText(String rawText, String sourceName) {
        // Fine-tuning / RAG 정확도 향상을 위한 데이터 노이즈 제거
        String cleanedText = cleanAndPreprocessText(rawText);

        // LangChain4j Document 객체로 변환 (메타데이터 삽입)
        Document document = Document.from(cleanedText);
        document.metadata().put("source", sourceName);

        // LangChain4j의 글자 수 기반 재귀적 분할 + 15% 오버랩 적용
        List<TextSegment> segments = DocumentSplitters
                .recursive(CHUNK_SIZE, OVERLAP_SIZE)
                .split(document);

        // 임베딩 수행 및 벡터 저장소에 적재 (루프 없이 한 번에 처리)
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);

        // 디스크에 저장 (ConfigReader 활용)
        String storePath = ConfigReader.getProperty("rag.store.path", "data/rag-store.json");
        embeddingStore.serializeToFile(storePath);

        System.out.println("✅ [" + sourceName + "] 인덱싱 완료 -> " + segments.size() + " 청크 생성 및 저장");
        return segments.size();
    }

    // ========================================================================
    // 데이터 가공 (전처리) 메서드
    // ========================================================================
    private String cleanAndPreprocessText(String text) {
        String processed = text.replaceAll("[ \\t]+", " "); // 다중 공백 압축
        processed = processed.replaceAll("(\\r?\\n\\s*){3,}", "\n\n"); // 과도한 줄바꿈 압축
        // 눈에 보이지 않는 제어문자 찌꺼기 제거
        processed = processed.replaceAll("[\\u0000-\\u0008\\u000B-\\u000C\\u000E-\\u001F]", "");
        return processed.trim();
    }


    // ========================================================================
    // Apache PDFBox를 이용한 PDF 텍스트 추출 및 TXT 처리
    // ========================================================================
    private String extractText(File file) throws Exception {
        String rawFileName = file.getName();
        String contextName = rawFileName.contains(".") 
                             ? rawFileName.substring(0, rawFileName.lastIndexOf(".")) 
                             : rawFileName;

        if (rawFileName.toLowerCase().endsWith(".txt")) {
            return "[문서명:" + contextName + "]\n" + Files.readString(file.toPath());
            
        } else if (rawFileName.toLowerCase().endsWith(".csv")) {
            // csv 처리 
            Document document = FileSystemDocumentLoader.loadDocument(file.toPath(), new MarkdownCsvParser());
    
            // 문서명 꼬리표 붙여주기
            return "[문서명:" + contextName + "]\n" + document.text();
            
        } else if (rawFileName.toLowerCase().endsWith(".pdf")) {
            // pdf 처리
            // LangChain4j 공식 PDFBox 파서 객체 생성
            LocalPdfParser pdfParser = new LocalPdfParser();
            
            // FileSystemDocumentLoader가 파일 열기/닫기, 텍스트 추출을 모두 알아서 처리
            Document document = FileSystemDocumentLoader.loadDocument(file.toPath(), pdfParser);
            
            // 추출된 텍스트 앞에 문서명을 붙여서 반환 (문맥 유지)
            return "[문서명:" + contextName + "]\n" + document.text();
        }
        return "";
    }

    private String htmlToMarkdownText(String html) {
        if (html == null || html.isEmpty()) return "";
        // html 쓰레기 데이터, CSS 코드 날림 
        String text = html.replaceAll("(?is)<(script|style|head|header|footer|nav|aside)[^>]*>.*?</\\1>", "");
        text = text.replaceAll("(?is)<h1[^>]*>(.*?)</h1>", "\n# $1\n");
        text = text.replaceAll("(?is)<h2[^>]*>(.*?)</h2>", "\n## $1\n");
        text = text.replaceAll("(?is)<h3[^>]*>(.*?)</h3>", "\n### $1\n");
        text = text.replaceAll("(?is)<li[^>]*>(.*?)</li>", "\n- $1");
        text = text.replaceAll("(?is)<(p|div|tr|br)[^>]*>", "\n");
        text = text.replaceAll("(?is)<[^>]+>", "");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\"");
        
        // 다중 공백 압축 및 과도한 줄바꿈 압축
        return text.replaceAll("[ \\t]+", " ").replaceAll("(\\r?\\n\\s*)+", "\n").trim();
    }
}