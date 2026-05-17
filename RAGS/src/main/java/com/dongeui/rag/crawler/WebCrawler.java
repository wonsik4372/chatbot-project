package com.dongeui.rag.crawler;

import com.dongeui.rag.config.AppConfig;
import com.dongeui.rag.model.KnowledgeCompiler;
import com.dongeui.rag.repository.VectorKnowledgeStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class WebCrawler {
    private final KnowledgeCompiler compiler;
    private final VectorKnowledgeStore store;

    public WebCrawler(KnowledgeCompiler compiler, VectorKnowledgeStore store) {
        this.compiler = compiler;
        this.store = store;
    }

    public void crawlAndIndexWeb(String startUrl) {
        System.out.println("🌐 [하위 메뉴 추적 크롤링] 학과 사이트 탐색을 시작합니다...");
        
        Set<String> visitedUrls = new HashSet<>();
        Queue<String> urlQueue = new LinkedList<>();
        urlQueue.add(startUrl);
        
        int maxPagesToCrawl = 35; 
        int crawledCount = 0;

        while (!urlQueue.isEmpty() && crawledCount < maxPagesToCrawl) {
            String currentUrl = urlQueue.poll();
            if (visitedUrls.contains(currentUrl)) continue;
            visitedUrls.add(currentUrl);
            crawledCount++;

            // 1차로 URL 기반의 안전한 고유 식별자 생성 (파일명 충돌 및 캐시 조회용)
            String urlToken = currentUrl.replaceAll("[^a-zA-Z0-9]", "_");
            
            // 💡 [캐시 검사용] 로컬 폴더에서 해당 URL 식별자로 시작하는 .md 파일이 있는지 먼저 스캔
            File cacheFile = findCacheFile(AppConfig.CACHE_DIR, urlToken);

            if (cacheFile != null && cacheFile.exists()) {
                System.out.println("⚡ [웹 캐시 적중] (" + crawledCount + ") " + currentUrl + " -> " + cacheFile.getName());
                try {
                    String cachedContent = Files.readString(cacheFile.toPath());
                    store.saveToVectorStore(cachedContent, "WebCrawl_" + cacheFile.getName(), "none", "none");
                    
                    // 💡 캐시 파일에서 링크 복원 추적 진행
                    discoverLinksFromCache(cachedContent, urlQueue, visitedUrls);
                    continue;
                } catch (IOException e) {
                    // 에러 시 재패치 처리 흐름으로 진행
                }
            }

            try {
                System.out.println("📥 [신규 페이지 웹 수집 중] (" + crawledCount + "/" + maxPagesToCrawl + ") -> " + currentUrl);
                Document doc = Jsoup.connect(currentUrl)
                        .timeout(10000)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .get();

                // 필요 없는 태그 지우기 전 링크 목록 먼저 수집 및 큐 배치
                List<String> discoveredLinks = discoverLinks(doc, urlQueue, visitedUrls, startUrl);

                doc.select("nav, footer, .admin, #header, .sidebar, script, style, .footer_content").remove();
                String pageTitle = doc.title().trim();
                String textContent = doc.body().text();

                if (textContent.trim().isEmpty()) continue;

                // 공학설계입문_김성우_정보공학관_911호 형식의 파일명을 위한 제목 정제
                String sanitizedTitle = pageTitle.replaceAll("[^a-zA-Z0-9가-힣_-]", "_")
                                                 .replaceAll("_+", "_"); // 연속된 언더바 정제
                
                if (sanitizedTitle.isEmpty() || sanitizedTitle.equals("_")) {
                    sanitizedTitle = "UntitledPage";
                }

                // 💡 최종 파일명 디자인 : [페이지제목_URL식별자.md] 
                // 뒤에 고유 식별자(urlToken)를 붙여주어야 같은 제목의 다른 페이지가 덮어씌워지지 않습니다.
                String finalFileName = sanitizedTitle + "_" + urlToken + ".md";
                File newCacheFile = new File(AppConfig.CACHE_DIR, finalFileName);

                String structuredMarkdown = compiler.compileWebContent(textContent, "웹페이지: " + pageTitle);

                // 💡 [핵심] 다음 실행 시 캐시 적중 상태에서도 하위 링크를 추적할 수 있도록 파일 하단에 링크 메타데이터를 주석으로 기록
                StringBuilder finalFileBody = new StringBuilder(structuredMarkdown);
                finalFileBody.append("\n\n");

                try (FileWriter writer = new FileWriter(newCacheFile)) {
                    writer.write(finalFileBody.toString());
                }

                store.saveToVectorStore(structuredMarkdown, "WebCrawl_" + finalFileName, "none", "none");

                // 로컬 Ollama 과부하 방지를 위한 대기 시간 대폭 부여
                Thread.sleep(2000);

            } catch (Exception e) {
                System.err.println("❌ 웹 페이지 수집 실패 [" + currentUrl + "]: " + e.getMessage());
            }
        }
        System.out.println("✅ [크롤링 완료] 총 " + visitedUrls.size() + "개의 학과 메뉴 페이지 지식을 동기화했습니다.");
    }

    // URL 토큰 값을 바탕으로 캐시 폴더 내에 이미 존재하는 파일이 있는지 매칭하는 헬퍼 메서드
    private File findCacheFile(String cacheDir, String urlToken) {
        File dir = new File(cacheDir);
        if (!dir.exists() || !dir.isDirectory()) return null;
        
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".md") && name.contains(urlToken));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }

    private List<String> discoverLinks(Document doc, Queue<String> urlQueue, Set<String> visitedUrls, String startUrl) {
        List<String> discovered = new ArrayList<>();
        try {
            URL base = new URL(startUrl);
            String host = base.getHost();

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absUrl = link.attr("abs:href").trim();
                if (absUrl.isEmpty() || absUrl.contains("#")) continue;
                
                if (absUrl.contains(host) && absUrl.contains("/se/")) {
                    if (absUrl.contains("download.do") || absUrl.contains("bbsMode=view")) {
                        continue; 
                    }
                    discovered.add(absUrl);
                    if (!visitedUrls.contains(absUrl) && !urlQueue.contains(absUrl)) {
                        urlQueue.add(absUrl);
                    }
                }
            }
        } catch (Exception e) {
            // 예외 무시
        }
        return discovered;
    }

    // 캐시 주석 스트림으로부터 하위 링크 리스트를 추출해 다음 추적 큐에 집어넣는 메서드
    private void discoverLinksFromCache(String cachedContent, Queue<String> urlQueue, Set<String> visitedUrls) {
        if (!cachedContent.contains("CACHE_LINKS_START")) return;
        try {
            String[] lines = cachedContent.split("\n");
            boolean insideLinkBlock = false;
            for (String line : lines) {
                if (line.contains("CACHE_LINKS_START")) {
                    insideLinkBlock = true;
                    continue;
                }
                if (line.contains("CACHE_LINKS_END")) {
                    break;
                }
                if (insideLinkBlock) {
                    String url = line.trim();
                    if (!url.isEmpty() && !visitedUrls.contains(url) && !urlQueue.contains(url)) {
                        urlQueue.add(url);
                    }
                }
            }
        } catch (Exception e) {
            // 예외 무시
        }
    }
}