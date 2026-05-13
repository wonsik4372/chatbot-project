import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.tika.Tika;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.parser.ParseContext;
import java.io.InputStream;
import java.io.FileInputStream;


/**
 * Ollama를 백엔드로 사용하는 RAG 시스템 (Gemma 4)
 */
public class RAGSystem {

    // ==========================================================
    //  Ollama 기본 API 엔드포인트
    // Ollama 서버가 로컬에서 실행되고 있어야 합니다.
    private static final String OLLAMA_BASE_URL = "http://localhost:11434/api";
    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    private static final String CHAT_MODEL = "gemma:2b"; // 사용하는 Gemma 모델
    private static final int EMBEDDING_DIM = 384; // all-MiniLM-L6-v2의 임베딩 차원 (가정)

    // 임베딩 벡터를 저장할 시뮬레이션 벡터 스토어 (실제는 DB 사용)
    private List<String> vectorStore = new ArrayList<>();
    
    // HTTP 클라이언트 초기화
    private final HttpClient httpClient = HttpClient.newBuilder().build();


    public static void main(String[] args) {
        RAGSystem system = new RAGSystem();

        // 1. 문서 로딩 및 인덱싱
        System.out.println("==============================================");
        System.out.println("烙 RAG 시스템 초기화 시작 (Ollama 기반)");
        System.out.println("문서가 담긴 폴더를 지정해주세요. (예: ./docs)");
        
        Scanner scanner = new Scanner(System.in);
        String documentPath = "";
        while (true) {
            System.out.print("문서 경로 입력: ");
            String path = scanner.nextLine();
            if (!path.isEmpty()) {
                documentPath = path;
                break;
            }
            System.out.println("경로가 유효하지 않습니다.");
        }

        try {
            system.indexDocuments(documentPath);
            System.out.println("\n 문서 로딩 및 임베딩 완료. 시스템 준비 완료.");

        } catch (IOException e) {
            System.err.println(" 초기화 오류: " + e.getMessage());
            return;
        }
        
        // 2. 반복적인 사용자 질의 응답 루프
        System.out.println("\n==============================================");
        System.out.println("[질의응답 모드] 종료하려면 '/bye'를 입력하세요.");
        
        while (true) {
            System.out.print("\n‍ 질문 > ");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("/bye")) {
                break;
            }
            if (input.trim().isEmpty()) {
                continue;
            }

            try {
                String answer = system.askQuestion(input);
                System.out.println("\n 답변 > " + answer);
            } catch (Exception e) {
                System.err.println("\n[오류 발생] 질문 처리 중 오류가 발생했습니다: " + e.getMessage());
            }
        }
        scanner.close();
    }

    /**
     * 파일 로딩 및 임베딩을 수행하여 벡터 스토어를 채웁니다.
     * @param directoryPath 문서가 담긴 폴더 경로
     */
    public void indexDocuments(String directoryPath) throws IOException {
        System.out.println("--- 문서 인덱싱 시작 ---");
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            throw new IOException("지정된 경로는 유효한 디렉토리가 아닙니다.");
        }

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt") || name.toLowerCase().endsWith(".pdf"));

        if (files == null || files.length == 0) {
            System.out.println(" 로드할 TXT 또는 PDF 파일이 없습니다. 이 단계는 건너뜁니다.");
            return;
        }

        for (File file : files) {
            try {
                String rawText = extractText(file);
                if (rawText.isEmpty()) continue;

                System.out.println("   [Processing] 파일 처리: " + file.getName());
                
                // 1. 청크 분할
                String[] chunks = chunkText(rawText, 500); 
                
                // 2. 임베딩 및 저장
                for (String chunk : chunks) {
                    // Ollama API를 사용하여 임베딩 벡터 생성
                   // indexDocuments 내부의 저장 로직
		List<Double> embedding = callOllamaApiForEmbedding(chunk);
		if (embedding != null && !embedding.isEmpty()) {
  		  // String 대신 DocumentChunk 객체를 저장!
    		vectorStore.add(new DocumentChunk(chunk, embedding)); 
		}
                }
            } catch (Exception e) {
                System.err.println("   [실패] 파일 처리 중 오류 발생 (" + file.getName() + "): " + e.getMessage());
            }
        }
        System.out.println("--- 인덱싱 완료. 총 " + vectorStore.size() + "개의 청크가 인덱스되었습니다. ---");
    }

    /**
     * 파일 내용 추출 (Tika 사용을 전제로 하되, 코드는 간단화)
     */
private String extractText(File file) {
    try (InputStream stream = new FileInputStream(file)) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1은 무제한
        Metadata metadata = new Metadata();
        parser.parse(stream, handler, metadata, new ParseContext());
        return handler.toString();
    } catch (Exception e) {
        System.err.println("   [Tika 오류] 파일 처리 실패 (" + file.getName() + "): " + e.getMessage());
        return "";
    }
}


    /**
     * 텍스트 청크 분할 (간단화된 구현)
     */
    private String[] chunkText(String text, int maxChunkSize) {
        // 실제로는 토큰 기반 청킹 라이브러리를 사용해야 합니다.
        // 여기서는 이해를 돕기 위해 1문단당 하나의 청크로 간주합니다.
        return text.split("[\\n\\r]+");
    }

    /**
     * Ollama API를 사용하여 임베딩 벡터를 요청합니다.
     * @param text 텍스트
     * @return 임베딩 벡터 (Double 리스트)
     */
    /**
 * Ollama API를 사용하여 임베딩 벡터를 요청합니다.
 */
// 임베딩 API 수정: 결과 JSON에서 숫자들만 추출
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
        // 정규식으로 숫자 추출 (파싱 라이브러리 없을 때 유용)
        Matcher m = Pattern.compile("-?\\d+\\.\\d+").matcher(response.body());
        while (m.find()) vector.add(Double.parseDouble(m.group()));
    }
    return vector;
}

// 생성 API 수정: JSON에서 "response" 필드 내용만 추출
private String callOllamaApiForGeneration(String prompt) throws IOException, InterruptedException {
    // ... (기존 payload 구성 로직) ...
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    
    String body = response.body();
    int start = body.indexOf("\"response\":\"") + 12;
    int end = body.indexOf("\",\"done\"");
    
    if (start > 11 && end > start) {
        return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
    return "답변 추출 실패: " + body;
}

    /**
     * 사용자의 질문을 받아 답변을 생성하는 RAG 핵심 로직입니다.
     * @param query 사용자의 질문
     * @return LLM이 생성한 답변
     */
    public String askQuestion(String query) throws IOException, InterruptedException {
        // 1. 쿼리 임베딩
        List<Double> queryEmbedding = callOllamaApiForEmbedding(query);

        // 2. 유사도 검색 (Retrieval)
        List<String> retrievedContexts = retrieveContext(queryEmbedding, 3);
        
        if (retrievedContexts.isEmpty()) {
            return " 현재 로드된 문서와 유사한 컨텍스트를 찾을 수 없습니다. (인덱싱 오류 또는 질문 범위를 벗어났을 수 있습니다.)";
        }

        // 3. 프롬프트 구성 및 생성 (Generation)
        String context = String.join("\n---\n", retrievedContexts);
        String prompt = buildPrompt(context, query);

        // 4. Ollama API 호출 (Chat Generation)
        return callOllamaApiForGeneration(prompt);
    }

    /**
     * 벡터 스토어에서 가장 유사한 문서를 검색합니다. (시뮬레이션)
     */
    private List<String> retrieveContext(List<Double> queryEmbedding, int topK) {
    if (vectorStore.isEmpty()) return new ArrayList<>();

    // 코사인 유사도가 높은 순으로 정렬하여 topK개 반환
    return vectorStore.stream()
            .sorted(Comparator.comparingDouble((DocumentChunk doc) -> 
                    calculateSimilarity(queryEmbedding, doc.vector)).reversed())
            .limit(topK)
            .map(doc -> doc.text)
            .collect(Collectors.toList());
}

// 유사도 계산 메서드 추가
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

    /**
     * LLM에게 전달할 최종 프롬프트를 만듭니다.
     * (시스템 프롬프트가 가장 중요합니다. AI의 역할과 제약을 정의합니다.)
     */
    private String buildPrompt(String context, String query) {
        return String.format(
            "당신은 전문 기업 비서 AI입니다. 당신의 목표는 제공된 [문맥] 정보만을 근거로 사용자의 [질문]에 정확하고 전문적인 어조로 답변하는 것입니다. " +
            "반드시 문맥을 참조해야 하며, 문맥에 답이 없다면 '제공된 문서에는 해당 정보가 없습니다.'라고 명확하게 답변하세요.\n\n" +
            "--- [문맥] ---\n%s\n\n" +
            "--- [질문] ---\n사용자 질문: %s", 
            context, 
            query
        );
    }

    /**
     * Ollama API를 사용하여 챗 답변을 요청합니다.
     * @param systemPrompt 최종 프롬프트
     * @return LLM의 답변 텍스트
     */
    private String callOllamaApiForGeneration(String prompt) throws IOException, InterruptedException {
    // 1. 프롬프트 내의 역슬래시(\)와 따옴표("), 줄바꿈(\n)을 JSON 규격에 맞게 변환합니다.
    String safePrompt = prompt.replace("\\", "\\\\")
                              .replace("\"", "\\\"")
                              .replace("\n", "\\n")
                              .replace("\r", "\\r");

    String payload = String.format(
        "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false}", 
        CHAT_MODEL, safePrompt
    );
    
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OLLAMA_BASE_URL + "/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        // 성공 시 응답 본문 출력
        return response.body(); 
    } else {
        return "오류 발생! 상태 코드: " + response.statusCode() + " 내용: " + response.body();
    }
}
// 클래스 내부에 추가
static class DocumentChunk {
    String text;
    List<Double> vector;
    DocumentChunk(String text, List<Double> vector) {
        this.text = text;
        this.vector = vector;
    }
}

// 필드 수정
private List<DocumentChunk> vectorStore = new ArrayList<>();
}