package com.cse.deu.campusbot.service;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RerankContentRetriever implements ContentRetriever {

    private final ContentRetriever originalRetriever;
    private final String rerankApiUrl = "http://127.0.0.1:8000/rerank"; // localhost 대신 127.0.0.1 명시
    private final int topK;
    private final RestTemplate restTemplate;

    public RerankContentRetriever(ContentRetriever originalRetriever, int topK) {
        this.originalRetriever = originalRetriever;
        this.topK = topK;
        this.restTemplate = new RestTemplate(); // 스프링의 표준 HTTP 클라이언트 사용
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> rawContents = originalRetriever.retrieve(query);
        if (rawContents == null || rawContents.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 1. JSON 바디 조립
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("query", query.text());

            JSONArray docsArray = new JSONArray();
            for (Content content : rawContents) {
                if (content != null && content.textSegment() != null) {
                    docsArray.put(content.textSegment().text());
                }
            }
            jsonBody.put("documents", docsArray);

            // 2. HTTP 헤더 설정 (UTF-8 인코딩 강제 명시)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));

            // 3. 요청 생성 및 전송
            HttpEntity<String> entity = new HttpEntity<>(jsonBody.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(rerankApiUrl, entity, String.class);

            // 4. 결과 처리 (상태코드가 2xx 일 때만 성공으로 처리)
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject responseJson = new JSONObject(response.getBody());
                JSONArray results = responseJson.getJSONArray("results");

                List<Content> rerankedContents = new ArrayList<>();
                for (int i = 0; i < Math.min(results.length(), topK); i++) {
                    int originalIndex = results.getJSONObject(i).getInt("index");
                    rerankedContents.add(rawContents.get(originalIndex));
                }
                return rerankedContents;
            } else {
                System.err.println("[Rerank Error] 서버 응답 오류: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("[Rerank Error] 통신 예외 발생: " + e.getMessage());
        }

        // 실패 시 시스템 다운 방지를 위한 폴백
        return rawContents.subList(0, Math.min(rawContents.size(), topK));
    }
}