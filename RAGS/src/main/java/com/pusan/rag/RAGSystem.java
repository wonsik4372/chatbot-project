package com.pusan.rag;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;

import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.Table;
import technology.tabula.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RAGSystem {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String OLLAMA_API_URL = "http://localhost:11434/api";

    public static void main(String[] args) throws Exception {
        RAGSystem system = new RAGSystem();
        StringBuilder allContext = new StringBuilder();
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n" + "=".repeat(65));
        System.out.println(" [SYSTEM] DEU CS RAG Intelligence System Initializing...");
        System.out.println(" [STATUS] Scanning local directory for academic resources...");
        System.out.println("=".repeat(65));

        // 1. 파일 분석
        File dir = new File("."); 
        File[] files = dir.listFiles((d, name) -> {
            String low = name.toLowerCase();
            return low.endsWith(".pdf") || low.endsWith(".csv") || 
                   low.endsWith(".xlsx") || low.endsWith(".xls") || 
                   low.endsWith(".txt");
        });

        int fileCount = 0;
        if (files != null) {
            for (File file : files) {
                System.out.print(String.format(" [%-12s] Loading: %-25s", "DATA_LOADER", file.getName()));
                try {
                    String data = "";
                    if (file.getName().toLowerCase().endsWith(".pdf")) data = system.extractWithStrictGrid(file);
                    else if (file.getName().toLowerCase().endsWith(".xlsx") || file.getName().toLowerCase().endsWith(".xls")) data = system.readExcelFile(file);
                    else data = system.readTextFile(file);
                    
                    if (!data.isEmpty()) {
                        System.out.println(" >> [SUCCESS]");
                        allContext.append("\n==================================================\n");
                        allContext.append(" [SOURCE_ID] ").append(file.getName()).append("\n");
                        allContext.append("--------------------------------------------------\n");
                        allContext.append(data);
                        allContext.append("\n==================================================\n");
                        fileCount++;
                    }
                } catch (Exception e) {
                    System.out.println(" >> [ERROR: " + e.getMessage() + "]");
                }
            }
        }

        // 2. 웹 크롤링
        System.out.println("\n" + "-".repeat(65));
        System.out.print(" [SYSTEM] Web Crawling URL (or 'skip'): ");
        String mainUrl = scanner.nextLine().trim();

        if (!mainUrl.equalsIgnoreCase("skip") && mainUrl.startsWith("http")) {
            List<String> links = system.extractImportantLinks(mainUrl);
            for (String link : links) {
                System.out.print(" [CRAWL_FETCH] " + link);
                String webData = system.readWebPage(link);
                if (!webData.isEmpty()) {
                    System.out.println(" -> [LOADED]");
                    allContext.append("\n==================================================\n");
                    allContext.append(" [WEB_SOURCE] ").append(link).append("\n");
                    allContext.append("--------------------------------------------------\n");
                    allContext.append(webData);
                    allContext.append("\n==================================================\n");
                }
            }
        }

        // 3. 최종 보고 및 질문 루프
        if (allContext.length() > 0) {
            System.out.println("\n" + "=".repeat(65));
            System.out.println(" [DEBUG] MEMORY LOAD COMPLETE (" + fileCount + " files)");
            System.out.println("=".repeat(65));
            
            while (true) {
                System.out.print("\n[질문 (종료: q)]: ");
                String query = scanner.nextLine();
                if (query.equalsIgnoreCase("q")) break;
                system.askQuestion(query, allContext.toString());
            }
        } else {
            System.out.println("⚠️ 데이터가 없습니다.");
        }
        scanner.close();
    }

    // PDF 추출 로직 정교화
    private String extractWithStrictGrid(File file) throws IOException {
        String roomName = file.getName().replace(".pdf", "");
        Map<Integer, Map<String, String>> scheduleMap = new HashMap<>();

        try (PDDocument document = PDDocument.load(file)) {
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
            Page page = oe.extract(1); 
            
            List<Table> tables = sea.extract(page);

            for (Table table : tables) {
                for (List<RectangularTextContainer> row : table.getRows()) {
                    if (row.size() < 2) continue;

                    String timeCell = row.get(0).getText().replace("\r", " ").trim();
                    if (!timeCell.contains("교시")) continue;

                    String timeSlot = timeCell.split("\\(")[0].trim();

                    for (int j = 1; j < row.size() && j <= 6; j++) {
                        String content = row.get(j).getText().replace("\r", " ").replace(" ", "").trim();
                        // 자습, 공강, 너무 짧은 텍스트 필터링
                        if (content.length() > 2 && !content.contains("자습") && !content.contains("공강")) {
                            scheduleMap.computeIfAbsent(j, k -> new HashMap<>()).put(timeSlot, cleanContent(content));
                        }
                    }
                }
            }
        }
        return convertToMarkdownTable(roomName, scheduleMap);
    }

    private String cleanContent(String content) {
        String cleaned = content.replace("(학)", "").replace("(대)", "").replace("-", "").trim();
        // 마지막 3글자를 교수명으로 추정하여 분리
        if (cleaned.length() >= 3) {
            String professor = cleaned.substring(cleaned.length() - 3);
            String subject = cleaned.substring(0, cleaned.length() - 3);
            return String.format("%s (교수: %s)", subject, professor);
        }
        return cleaned;
    }

    private String convertToMarkdownTable(String roomName, Map<Integer, Map<String, String>> scheduleMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(roomName).append(" 강의실\n\n");
        sb.append("| 시간 | 월 | 화 | 수 | 목 | 금 | 토 |\n");
        sb.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |\n");
        
        String[] times = {"1교시", "2교시", "3교시", "4교시", "5교시", "6교시", "7교시", "8교시", "9교시"};
        for (String time : times) {
            sb.append("| ").append(time).append(" | ");
            for (int d = 1; d <= 6; d++) {
                sb.append(scheduleMap.getOrDefault(d, new HashMap<>()).getOrDefault(time, "-")).append(" | ");
            }
            sb.append("\n");
        }

        sb.append("\n### [텍스트 요약 버전]\n");
        String[] days = {"", "월요일", "화요일", "수요일", "목요일", "금요일"};
        for (int d = 1; d <= 5; d++) {
            sb.append(days[d]).append(": ");
            List<String> lessons = new ArrayList<>();
            for (String time : times) {
                String content = scheduleMap.getOrDefault(d, new HashMap<>()).getOrDefault(time, "-");
                if (!content.equals("-")) lessons.add(time + "[" + content + "]");
            }
            sb.append(lessons.isEmpty() ? "공강" : String.join(", ", lessons)).append("\n");
        }
        return sb.toString();
    }

    // (기타 보조 메서드: extractImportantLinks, readWebPage, readTextFile, readExcelFile 등은 기존 유지)
    private List<String> extractImportantLinks(String baseUrl) {
        List<String> links = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(baseUrl).timeout(5000).get();
            for (Element link : doc.select("a[href]")) {
                String text = link.text().replace(" ", "");
                String href = link.attr("abs:href");
                if (text.matches(".*(교수|교육과정|학과소개|학사안내|졸업|시간표|실습실|진로).*")) {
                    if (!links.contains(href) && href.startsWith(baseUrl.substring(0, baseUrl.lastIndexOf("/")))) {
                        links.add(href);
                    }
                }
            }
        } catch (IOException e) { }
        return links;
    }

    private String readWebPage(String url) {
        try {
            Document doc = Jsoup.connect(url).timeout(10000).get();
            Elements contents = doc.select("div#content, div#container, .sub_content");
            if (contents.isEmpty()) contents = new Elements(doc.body());
            contents.select("script, style, nav, footer").remove();
            return "\n## " + doc.title() + "\n" + contents.text();
        } catch (Exception e) { return ""; }
    }

    private String readTextFile(File file) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (IOException e) { return ""; }
    }

    private String readExcelFile(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = file.getName().endsWith(".xlsx") ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis)) {
            StringBuilder sb = new StringBuilder();
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    sb.append(cell.toString()).append("\t");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    public void askQuestion(String question, String context) throws Exception {
        // --- [디버그: AI 학습 데이터 선제 출력] ---
        System.out.println("\n" + "=".repeat(25) + " [DEBUG: LEARNED DATA] " + "=".repeat(25));
        System.out.println(context); 
        System.out.println("=".repeat(72) + "\n");

        System.out.println("🤖 조교가 데이터를 분석 중입니다...");
        
        String structuredPrompt = 
            "### [Persona]\n" +
            "당신은 동의대학교 컴퓨터소프트웨어공학전공의 친절하고 정확한 '학과 조교 AI'입니다.\n\n" +
            "### [Instructions]\n" +
            "1. 아래 제공된 [학습 데이터]만을 근거로 학생의 질문에 답변하세요.\n" +
            "2. 데이터에 없는 내용은 추측하지 말고 '확인하기 어렵다'고 답하세요.\n" +
            "3. 답변 끝에는 '추가로 궁금한 점이 있으면 과사무실로 문의주세요.'를 붙여주세요.\n\n" +
            "### [학습 데이터]\n" + 
            context + "\n\n" +
            "### [학생 질문]\n" + 
            question + "\n\n" +
            "### [AI 조교 답변]";

        HttpClient client = HttpClient.newHttpClient();
        Map<String, Object> map = new HashMap<>();
        map.put("model", "gemma2:9b");
        map.put("prompt", structuredPrompt);
        map.put("stream", false);
        map.put("options", Map.of("temperature", 0.3));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_API_URL + "/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(map)))
                .build();
        
        try {
            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            String aiAnswer = objectMapper.readTree(response).get("response").asText();
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println(aiAnswer);
            System.out.println("=".repeat(60));
        } catch (Exception e) {
            System.err.println("❌ Ollama 연결 실패: " + e.getMessage());
        }
    }
}