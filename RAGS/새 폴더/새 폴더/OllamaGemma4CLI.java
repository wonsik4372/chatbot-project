import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaGemma4CLI {

    // Ollama 기본 API 주소
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    // 요청한 모델명 (Ollama 공식 레포지토리에서 gemma4가 릴리즈되면 즉시 사용 가능)
    private static final String MODEL_NAME = "gemma4:latest";
    // HttpClient는 스레드 안전하므로 인스턴스를 재사용
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println(" 사용법: java OllamaGemma4CLI \"질문 내용\"");
            System.err.println(" 예시: java OllamaGemma4CLI \"자바에서 RAG를 적용하는 장점은?\"");
            System.exit(1);
        }

        // 명령행 인자를 공백 기준으로 결합하여 단일 쿼리로 처리
        String userQuery = String.join(" ", args);
        System.out.println(" 입력된 질문: " + userQuery + "\n");

        try {
            String answer = queryOllama(userQuery);
            System.out.println("烙 Gemma4 답변:\n" + answer);
        } catch (Exception e) {
            System.err.println(" 요청 실패: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String queryOllama(String query) throws Exception {
        // JSON 페이로드 생성 시 특수문자 안전 이스케이핑 처리
        String escapedQuery = query
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String jsonBody = String.format(
                "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false}",
                MODEL_NAME, escapedQuery
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API 오류 (HTTP " + response.statusCode() + "): " + 
response.body());
        }

        return parseResponseJson(response.body());
    }

   private static String parseResponseJson(String json) {
    // 1. "response":" 부분을 찾습니다.
    String key = "\"response\":\"";
    int startIdx = json.indexOf(key);
    if (startIdx == -1) throw new RuntimeException("응답 파싱 실패: response 키를 찾을 수 없음");

    startIdx += key.length();
    
    // 2. 닫는 따옴표를 찾되, 이스케이프된 따옴표(\")는 무시합니다.
    StringBuilder sb = new StringBuilder();
    boolean escaped = false;
    for (int i = startIdx; i < json.length(); i++) {
        char c = json.charAt(i);
        if (escaped) {
            sb.append(c);
            escaped = false;
        } else if (c == '\\') {
            escaped = true;
            sb.append(c);
        } else if (c == '\"') {
            break; // 실제 닫는 따옴표를 만나면 종료
        } else {
            sb.append(c);
        }
    }

    // JSON 이스케이프 문자 복원 (\n 등)
    return sb.toString()
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
}
}