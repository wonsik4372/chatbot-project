import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class RAGSystem {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434/api";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final String CHAT_MODEL = "gemma:2b";

    // 1. 데이터 구조 정의 (텍스트와 벡터를 동시에 저장)
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

        System.out.println("🚀 RAG 시스템 시작 (Ollama 기반)");
        System.out.print("문서 폴더 경로 입력 (예: ./docs): ");
        String path = scanner.nextLine();

        try {
            system.indexDocuments(path);
            System.out.println("\n✅ 인덱싱 완료! 질문을 시작하세요. (종료: /bye)");
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

    public void indexDocuments(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".txt") || name.toLowerCase().endsWith(".pdf"));

        if (files == null || files.length == 0) return;

        for (File file : files) {
            try {
                System.out.print("📄 " + file.getName() + " 분석 중... ");
                String rawText = extractText(file);
                String[] chunks = chunkText(rawText, 1500);

                for (String chunk : chunks) {
                    List<Double> embedding = callOllamaApiForEmbedding(chunk);
                    if (embedding != null && !embedding.isEmpty()) {
                        vectorStore.add(new DocumentChunk(chunk, embedding));
                    }
                }
                System.out.println("완료.");
            } catch (Exception e) {
                System.err.println("실패: " + e.getMessage());
            }
        }
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
    // 텍스트가 너무 짧으면 그대로 반환
    if (text.length() <= maxChunkSize) return new String[]{text};
    
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
        int end = Math.min(start + maxChunkSize, text.length());
        chunks.add(text.substring(start, end));
        // 100자 정도 겹치게 하여 문맥이 끊기는 것을 방지 (Overlap)
        start += (maxChunkSize - 100); 
    }
    return chunks.toArray(new String[0]);
}

    // 2. 임베딩 벡터 추출 (JSON 파싱 로직 포함)
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
            // 숫자를 더 정확하게 매칭하는 정규식으로 교체
Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?").matcher(response.body());
            while (m.find()) {
                vector.add(Double.parseDouble(m.group()));
            }
        }
        return vector;
    }

  public String askQuestion(String query) throws IOException, InterruptedException {
    List<Double> queryEmbedding = callOllamaApiForEmbedding(query);
    // topK를 5개 이상으로 늘려 데이터가 잘린 부분을 보충합니다.
    List<String> retrievedContexts = retrieveContext(queryEmbedding, 6);

    String context = String.join("\n", retrievedContexts);
    
    // 프롬프트에 '표 데이터' 특성을 반영
    String prompt = String.format(
    "당신은 강의실 시간표 안내 비서입니다. 제공된 [문맥]은 PDF에서 추출되어 표 구조가 흐트러진 텍스트입니다.\\n\\n" +
    "### 답변 규칙:\\n" +
    "1. [문맥] 상단에 요일 헤더가 있고, 중간에 교시(1교시~9교시)가 있으며, 하단에 과목 리스트가 나열되는 구조입니다.\\n" +
    "2. '1교시'가 월요일부터 금요일까지 순서대로 나열되어 있다고 가정하고, 질문한 요일에 맞는 과목을 하단 리스트에서 찾아내세요.\\n" +
    "3. 예를 들어, 리스트의 첫 번째 과목은 보통 월요일 수업입니다.\\n" +
    "4. 확실하지 않다면 문맥에 나온 과목 이름들을 모두 나열하며 확인을 요청하세요.\\n\\n" +
    "### [문맥]:\\n%s\\n\\n" +
    "### [질문]:\\n%s", 
    context.replace("\n", " "), query);
        
    return callOllamaApiForGeneration(prompt);
}

    // 3. 실제 코사인 유사도 검색 구현
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

    // 4. 답변 생성 (JSON에서 순수 텍스트만 추출)
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