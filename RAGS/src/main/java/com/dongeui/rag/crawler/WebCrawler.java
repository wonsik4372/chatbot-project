package com.dongeui.rag.crawler;

import com.dongeui.rag.config.AppConfig;
import com.dongeui.rag.llm.KnowledgeCompiler;
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

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

            String safeFileName = currentUrl.replaceAll("[^a-zA-Z0-9]", "_") + ".cache.txt";
            File cacheFile = new File(AppConfig.CACHE_DIR, safeFileName);

            if (cacheFile.exists()) {
                System.out.println("⚡ [웹 캐시 적중] (" + crawledCount + ") " + currentUrl);
                try {
                    String cachedMarkdown = Files.readString(cacheFile.toPath());
                    store.saveToVectorStore(cachedMarkdown, "WebCrawl_" + safeFileName, "none", "none");
                    
                    Document doc = Jsoup.parse(cachedMarkdown, currentUrl);
                    discoverLinks(doc, urlQueue, visitedUrls, startUrl);
                    continue;
                } catch (IOException e) {
                    // 에러 시 재패치
                }
            }

            try {
                System.out.println("📥 [신규 페이지 웹 수집 중] (" + crawledCount + "/" + maxPagesToCrawl + ") -> " + currentUrl);
                Document doc = Jsoup.connect(currentUrl)
                        .timeout(10000)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .get();

                discoverLinks(doc, urlQueue, visitedUrls, startUrl);

                doc.select("nav, footer, .admin, #header, .sidebar, script, style, .footer_content").remove();
                String pageTitle = doc.title();
                String textContent = doc.body().text();

                if (textContent.trim().isEmpty()) continue;

                String structuredMarkdown = compiler.compileWebContent(textContent, "웹페이지: " + pageTitle);

                try (FileWriter writer = new FileWriter(cacheFile)) {
                    writer.write(structuredMarkdown);
                }

                store.saveToVectorStore(structuredMarkdown, "WebCrawl_" + pageTitle, "none", "none");

                // 로컬 Ollama 과부하 방지를 위한 대기 시간 대폭 부여
                Thread.sleep(2000);

            } catch (Exception e) {
                System.err.println("❌ 웹 페이지 수집 실패 [" + currentUrl + "]: " + e.getMessage());
            }
        }
        System.out.println("✅ [크롤링 완료] 총 " + visitedUrls.size() + "개의 학과 메뉴 페이지 지식을 동기화했습니다.");
    }

    private void discoverLinks(Document doc, Queue<String> urlQueue, Set<String> visitedUrls, String startUrl) {
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
                    if (!visitedUrls.contains(absUrl) && !urlQueue.contains(absUrl)) {
                        urlQueue.add(absUrl);
                    }
                }
            }
        } catch (Exception e) {
            // 파싱 예외 무시
        }
    }
}