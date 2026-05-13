import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RAGChatbot{

    private static final String OLLAMA_BASE_URL = "http://localhost:11434/api";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final String CHAT_MODEL = "gemma:2b";

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final Tika tika = new Tika();

    // 벡터 스토어
    private List<DocumentChunk> vectorStore = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        RAGChatbot bot = new RAGChatbot();
        Scanner scanner = new Scanner(System.in);

        System.out.println("📂 문서/URL을 입력하세요 (예: ./docs 또는 https://example.com)");
        String inputPath = scanner.nextLine();

        if (inputPath.startsWith("http")) {
            String text = bot.extractFromUrl(inputPath);
            bot.indexText(text);
        } else {
            bot.indexDocuments(inputPath);
        }

        System.out.println("✅ 인덱싱 완료. 챗봇 모드 시작!");
        while (true) {
            System.out.print("\n🙋 질문 > ");
            String query = scanner.nextLine();
            if (query.equalsIgnoreCase("/bye")) break;

            String answer = bot.askQuestion(query);
            System.out.println("\n🤖 답변 > " + answer);
        }
        scanner.close();
    }

    // ===================== 문서 인덱싱 =====================
    public void indexDocuments(String directoryPath) throws Exception {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".txt") || n.endsWith(".pdf") || n.endsWith(".xlsx");
        });

        if (files == null || files.length == 0) {
            System.out.println("⚠️ 지원하는 문서 없음");
            return;
        }

        for (File file : files) {
            System.out.println("📄 " + file.getName() + " 처리 중...");
            String text = tika.parseToString(file);
            indexText(text);
        }
    }

    public void indexText(String text) throws Exception {
        int chunkSize = 800, overlap = 100;
        for (int i = 0; i < text.length(); i += (chunkSize - overlap)) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end).trim();
            if (chunk.length() < 20) continue;
            List<Double> embedding = callOllamaApiForEmbedding(chunk);
            vectorStore.add(new DocumentChunk(chunk, embedding));
        }
        System.out.println("✨ 총 " + vectorStore.size() + "개의 청크 저장 완료");
    }

    private String extractFromUrl(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        return doc.text();
    }

    // ===================== Ollama API =====================
    private List<Double> callOllamaApiForEmbedding(String text) throws IOException, InterruptedException {
        String payload = String.format("{\"model\":\"%s\",\"input\":\"%s\"}", EMBEDDING_MODEL, text);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode node = new ObjectMapper().readTree(response.body());
            JsonNode arr = node.get("embedding");
            List<Double> vector = new ArrayList<>();
            for (JsonNode val : arr) vector.add(val.asDouble());
            return vector;
        }
        throw new RuntimeException("Embedding 실패: " + response.body());
    }

    private String callOllamaApiForGeneration(String prompt) throws IOException, InterruptedException {
        String safePrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String payload = String.format("{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}", CHAT_MODEL, safePrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode node = new ObjectMapper().readTree(response.body());
            return node.get("response").asText();
        }
        return "❌ 오류: " + response.body();
    }

    // ===================== RAG 핵심 =====================
    public String askQuestion(String query) throws Exception {
        List<Double> queryEmbedding = callOllamaApiForEmbedding(query);
        List<String> contexts = retrieveContext(queryEmbedding, 3);

        if (contexts.isEmpty()) return "문맥 없음";

        String context = String.join("\n---\n", contexts);
        String prompt = String.format(
                "당신은 전문 비서 AI입니다. 제공된 문맥만 근거로 답변하세요.\n\n[문맥]\n%s\n\n[질문]\n%s",
                context, query);

        return callOllamaApiForGeneration(prompt);
    }

    private List<String> retrieveContext(List<Double> queryEmbedding, int topK) {
        return vectorStore.stream()
                .sorted((a, b) -> Double.compare(
                        cosineSimilarity(queryEmbedding, b.embedding),
                        cosineSimilarity(queryEmbedding, a.embedding)))
                .limit(topK)
                .map(dc -> dc.text)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        double dot = 0, norm1 = 0, norm2 = 0;
        for (int i = 0; i < v1.size(); i++) {
            dot += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // ===================== 내부 클래스 =====================
    static class DocumentChunk {
        String text;
        List<Double> embedding;
        DocumentChunk(String text, List<Double> embedding) {
            this.text = text;
            this.embedding = embedding;
        }
    }
}
