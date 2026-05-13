package com.pusan.rag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

// --- Tika 관련 (기본 텍스트 추출) ---
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

// --- POI 관련 (엑셀 추출) ---
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// --- Tabula 관련 (PDF 표 추출) ---
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import org.apache.pdfbox.pdmodel.PDDocument;

public class RAGSystem {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434/api";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final String CHAT_MODEL = "gemma:7b"; // 
    static class DocumentChunk {
        String text;
        List<Double> vector;
        DocumentChunk(String text, List<Double> vector) {
            this.text = text;
            this.vector = vector;
        }
    }

    private List<DocumentChunk> vectorStore = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public static void main(String[] args) {
        RAGSystem system = new RAGSystem();
        Scanner scanner = new Scanner(System.in);

        System.out.println("🚀 [Hybrid 데이터 인덱싱 모드] RAG 시스템 시작");
        System.out.println("지원 형식: PDF, TXT, XLSX (Excel)");
        System.out.print("문서 폴더 경로 입력: ");
        String path = scanner.nextLine();

        try {
            system.indexDocuments(path);
            System.out.println("\n✅ 학습 완료! 이제 질문하세요. (종료: /bye)");
        } catch (Exception e) {
            System.err.println("❌ 초기화 오류: " + e.getMessage());
            return;
        }

        while (true) {
            System.out.print("\n🤔 질문 > ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("/bye")) break;
            if (input.trim().isEmpty()) continue;

            try {
                System.out.println("\n🤖 답변 > " + system.askQuestion(input));
            } catch (Exception e) {
                System.err.println("오류 발생: " + e.getMessage());
            }
        }
        scanner.close();
    }

    // [통합] PDF, TXT, XLSX를 모두 처리하는 인덱싱 로직
    public void indexDocuments(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".txt") || 
            name.toLowerCase().endsWith(".pdf") ||
            name.toLowerCase().endsWith(".xlsx"));

        if (files == null || files.length == 0) {
            System.out.println("⚠️ 해당 경로에 읽을 수 있는 파일이 없습니다.");
            return;
        }

        for (File file : files) {
            try {
                String fileName = file.getName().toLowerCase();
                System.out.print("📄 " + fileName + " 데이터 추출 중... ");
                
                String rawText;
                if (fileName.endsWith(".xlsx")) {
                    rawText = extractTextFromExcel(file);
                } else {
                    rawText = extractText(file);
                }
                
                System.out.print("AI 정규화(Markdown) 중... ");
                // 엑셀로 정밀하게 읽었더라도 AI를 한 번 거쳐 완벽한 표 구조로 만듭니다.
                String normalizedText = convertToMarkdownViaAI(rawText);

                // 마크다운 표 구조 보존을 위해 청크 단위를 크게 잡음
                String[] chunks = chunkText(normalizedText, 2500);

                for (String chunk : chunks) {
                    List<Double> embedding = callOllamaApiForEmbedding(chunk);
                    if (embedding != null && !embedding.isEmpty()) {
                        vectorStore.add(new DocumentChunk(chunk, embedding));
                    }
                }
                System.out.println("성공.");
            } catch (Exception e) {
                System.err.println("실패: " + e.getMessage());
            }
        }
    }

    // 엑셀 전용 추출 로직: 행과 열의 구조를 보존하며 텍스트화
    private String extractTextFromExcel(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 처리
            for (Row row : sheet) {
                sb.append("| ");
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String value = formatCellValue(cell);
                    sb.append(value).append(" | ");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String formatCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: 
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                return String.valueOf((int)cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }

    private String convertToMarkdownViaAI(String rawText) throws IOException, InterruptedException {
        String prompt = "너는 시간표 데이터 전문가야. 제공된 텍스트는 강의실 시간표 데이터야. " +
                        "내용을 분석해서 '| 교시(시간) | 월 | 화 | 수 | 목 | 금 |' 컬럼을 가진 " +
                        "표준 마크다운(Markdown) 표 형식으로 엄격하게 정리해줘. " +
                        "수업이 없는 빈 칸은 반드시 공백으로 유지하고, 다른 설명 없이 오직 마크다운 표만 출력해.\n\n" + rawText;
        return callOllamaApiForGeneration(prompt);
    }

    private String extractText(File file) throws Exception {
        try (InputStream stream = new FileInputStream(file)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            parser.parse(stream, handler, metadata, new ParseContext());
            return handler.toString();
        }
    }

    private String[] chunkText(String text, int maxChunkSize) {
        if (text.length() <= maxChunkSize) return new String[]{text};
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.length() + line.length() > maxChunkSize) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks.toArray(new String[0]);
    }

    private List<Double> callOllamaApiForEmbedding(String text) throws IOException, InterruptedException {
        String safeText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        String payload = String.format("{\"model\": \"%s\", \"input\": \"%s\"}", EMBEDDING_MODEL, safeText);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<Double> vector = new ArrayList<>();
        if (response.statusCode() == 200) {
            Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?").matcher(response.body());
            while (m.find()) vector.add(Double.parseDouble(m.group()));
        }
        return vector;
    }

    public String askQuestion(String query) throws IOException, InterruptedException {
        List<Double> queryEmbedding = callOllamaApiForEmbedding(query);
        List<String> retrievedContexts = retrieveContext(queryEmbedding, 5);
        String context = String.join("\n\n", retrievedContexts);
        
        String prompt = String.format(
            "너는 대학 강의실 시간표 분석 비서야. 아래 [문맥]은 강의실별 시간표(마크다운 표) 데이터야.\\n\\n" +
            "### 분석 지침:\\n" +
            "1. 열 순서: | 시간 | 월 | 화 | 수 | 목 | 금 |\\n" +
            "2. 빈 칸은 수업이 없음을 의미한다.\\n" +
            "3. '점심 시간' 질문 시 12:00~13:50 행을 확인한다.\\n" +
            "4. 교수님 성함은 보통 과목명 옆 괄호() 안에 있다.\\n\\n" +
            "### [문맥]:\\n%s\\n\\n" +
            "### [질문]:\\n%s", 
            context, query);
            
        return callOllamaApiForGeneration(prompt);
    }

    private List<String> retrieveContext(List<Double> queryEmbedding, int topK) {
        return vectorStore.stream()
                .sorted(Comparator.comparingDouble((DocumentChunk doc) -> 
                        calculateSimilarity(queryEmbedding, doc.vector)).reversed())
                .limit(topK)
                .map(doc -> doc.text)
                .collect(Collectors.toList());
    }

    private double calculateSimilarity(List<Double> v1, List<Double> v2) {
        double dot = 0, n1 = 0, n2 = 0;
        int len = Math.min(v1.size(), v2.size());
        for (int i = 0; i < len; i++) {
            dot += v1.get(i) * v2.get(i);
            n1 += v1.get(i) * v1.get(i);
            n2 += v2.get(i) * v2.get(i);
        }
        return (n1 == 0 || n2 == 0) ? 0 : dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    private String callOllamaApiForGeneration(String prompt) throws IOException, InterruptedException {
        String safePrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String payload = String.format("{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false}", 
                                        CHAT_MODEL, safePrompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        int start = body.indexOf("\"response\":\"") + 12;
        int end = body.indexOf("\",\"done\"");
        if (start > 11 && end > start) {
            return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
        }
        return "답변 생성 실패: " + body;
    }
}