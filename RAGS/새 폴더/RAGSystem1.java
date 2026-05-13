import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.tika.Tika;

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
    public void indexDocuments(String directoryPath) throws Exception {
    File dir = new File(directoryPath);
    // .txt, .pdf, .xlsx 확장자 모두 포함
    File[] files = dir.listFiles((d, name) -> {
        String wideName = name.toLowerCase();
        return wideName.endsWith(".txt") || wideName.endsWith(".pdf") || wideName.endsWith(".xlsx");
    });

    if (files == null || files.length == 0) {
        System.out.println("⚠️ 지정된 경로에 지원하는 문서 파일이 없습니다.");
        return;
    }

    for (File file : files) {
        System.out.print("📄 " + file.getName() + " 분석 중... ");
        
        // Tika가 엑셀의 셀 데이터를 탭(\t)이나 공백으로 구분하여 가져옵니다.
        String rawText = tika.parseToString(file);
        
        // 엑셀 특유의 연속된 공백이나 의미 없는 제어 문자를 정리합니다.
        String cleanedText = rawText.replaceAll("[\\t ]+", " ").trim();

        // 엑셀은 행 단위 정보가 중요하므로, 문맥을 넉넉하게 800자 단위로 잡습니다.
        int chunkSize = 800;
        int overlap = 100;
        int count = 0;

        if (cleanedText.length() <= chunkSize) {
            // 문서가 짧으면 통째로 저장
            saveChunk(cleanedText);
            count++;
        } else {
            for (int i = 0; i < cleanedText.length(); i += (chunkSize - overlap)) {
                int end = Math.min(i + chunkSize, cleanedText.length());
                String chunk = cleanedText.substring(i, end);
                
                if (chunk.length() < 15) continue;

                saveChunk(chunk);
                count++;
                if (end == cleanedText.length()) break;
            }
        }
        System.out.println(count + "개의 문맥 조각 생성 완료.");
    }
    System.out.println("✨ 총 " + vectorStore.size() + "개의 지식이 데이터베이스에 저장되었습니다.");
}

// 중복 코드를 줄이기 위한 헬퍼 메서드
private void saveChunk(String text) throws Exception {
    List<Double> vector = getEmbedding(text);
    if (vector != null) {
        vectorStore.add(new DocumentChunk(text, vector));
    }
}
    /**
     * 파일 내용 추출 (Tika 사용을 전제로 하되, 코드는 간단화)
     */
    private String extractText(File file) throws Exception {
        // **[실제 구현 필요]: Tika 라이브러리를 사용하여 PDF/TXT 텍스트를 추출해야 합니다.**
        if (file.getName().toLowerCase().endsWith(".txt")) {
            return Files.readString(file.toPath());
        } else if (file.getName().toLowerCase().endsWith(".pdf")) {
            // PDFBox 또는 Tika로 PDF 텍스트 추출 로직을 여기에 구현해야 합니다.
            return "회사의 새 정책은 모든 팀원에게 공지되었습니다. 이 정책은 2024년 7월 1일부터 적용되며, 모든 직원은 필히 숙지해야 합니다. (PDF 더미 텍스트)";
        }
        return "";
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
private List<Double> callOllamaApiForEmbedding(String text) throws IOException, InterruptedException {
    // 1. URL 수정: /api/embeddings -> /embed (404 에러 방지)
    // 2. 필드 수정: prompt -> input (nomic-embed-text 규격)
    String payload = String.format("{\"model\": \"%s\", \"input\": \"%s\"}", EMBEDDING_MODEL, text);
    
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OLLAMA_BASE_URL + "/embed")) // 주소 확인!
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        // 성공 시 로그
        return List.of(0.1, 0.2); // (실제 파싱 로직 전까지 임시 반환)
    } else {
        System.err.println("   [API Call] Embedding Failed. Status: " + response.statusCode() + " - " + response.body());
        return null;
    }
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
    // 저장된 모든 문서를 LLM에게 참고하라고 전달 (데이터가 적을 때 유효)
    return vectorStore.stream().limit(10).collect(Collectors.toList());
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
}