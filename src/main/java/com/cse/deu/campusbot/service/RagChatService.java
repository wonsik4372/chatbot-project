/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 질문 임베딩, 벡터 검색, 답변 생성
 * @author wonsik
 */
public class RagChatService {
    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    // 정확도를 위해 추출할 최대 문맥 개수 (기존 TOP_K = 10 유지)
    private static final int MAX_RESULTS = 5;
    
    // 최소 유사도 점수 (0.0 ~ 1.0)
    // 이 점수보다 낮은 관련도의 데이터 무시
    private static final double MIN_SCORE = 0.57;

    public RagChatService(ChatLanguageModel chatModel, 
                          EmbeddingModel embeddingModel, 
                          EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 프론트엔드에서 넘어온 질문(query)에 대한 최종 답변을 반환합니다.
     */
    public String askQuestion(String query) {
        System.out.println("\n[진행 알림] 사용자 질문 수신: " + query);
        
        // LangChain4j 검색 증강(Retrieval) 기능
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content()) // 질문을 벡터로 변환
                .maxResults(MAX_RESULTS)                               // 상위 10개 추출
                .minScore(MIN_SCORE)                                   // 최소 연관성 필터링
                .build();
        
        // ##### 정보를 찾을 수 없다고 뜨는거면 여기가 문제 아님? #####
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
        
        System.out.println("\n📊 [검색된 청크 유사도 결과 Top " + matches.size() + "]");
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);

            // 1. 유사도 점수 (0.0 ~ 1.0)
            double score = match.score(); 

            // 2. 메타데이터 (출처)
            String source = match.embedded().metadata().getString("source");

            // 3. 텍스트 내용 (너무 길면 잘라서 출력)
            String textSnippet = match.embedded().text().replaceAll("\\n", " ");
            if (textSnippet.length() > 50) {
                textSnippet = textSnippet.substring(0, 50) + "...";
            }

            // 보기 좋게 포맷팅해서 출력
            System.out.printf("   👉 [%d위] 점수: %.4f | 출처: %s | 내용: %s\n", 
                              i + 1, score, source, textSnippet);
        }
        System.out.println("=======================================================\n");
    
        // 검색된 문맥이 없을 경우의 방어 로직 (환각/지어내기 방지)
        if (matches.isEmpty()) {
            System.out.println("⚠️ 관련 문맥을 찾을 수 없습니다.");
            return "죄송합니다. 현재 학습된 학교 데이터 중에서는 해당 질문에 대한 정보를 찾을 수 없습니다.";
        }

        // 검색된 데이터(청크)들을 하나의 거대한 문자열(Context)로 취합
        String context = matches.stream()
                .map(match -> {
                    String source = match.embedded().metadata().getString("source");
                    return "[" + source + "]\n" + match.embedded().text();
                })
                .collect(Collectors.joining("\n\n"));

        System.out.println("🔍 [디버깅] AI에게 전달될 문맥 데이터 개수: " + matches.size() + "개");

        // 프롬프트(Prompt) 엔지니어링 
        String systemPrompt = String.format(
            "### 역할\n" +
            "너는 대학교 학사 정보 안내를 담당하는 지능형 어시스턴트야.\n\n" +
            "### 임무\n" +
            "제공된 [참고 데이터]를 꼼꼼히 분석하여 학생의 질문에 답해줘. 데이터의 형식이 불규칙하더라도 문맥을 파악해서 최대한 정보를 찾아내야 해.\n\n" +
            "### 분석 가이드\n" +
            "1. **키워드 매칭**: 질문에 포함된 요일(예: 수), 장소(예: 916), 과목명 등을 데이터에서 찾아.\n" +
            "2. **정보 결합**: 정보가 여러 줄에 나뉘어 있어도(예: 한 줄에는 시간, 다음 줄에는 과목명) 서로 인접해 있다면 하나의 수업 정보로 간주해.\n" +
            "3. **추론**: '수 1교시'와 '09:00'이 같이 있다면 이를 연결해서 이해해.\n\n" +
            "### 참고 데이터\n%s\n\n" +
            "### 학생 질문\n%s\n\n" +
            "### 답변 원칙\n" +
            "- 데이터에 근거가 있다면 절대 '정보가 없다'고 하지 말 것.\n" +
            "- 답변은 읽기 좋게 불렛 포인트로 정리해줘.",
            context, query
        );

        System.out.println("[진행 알림] AI가 답변을 생성 중입니다...");

        // LLM(Gemma4) API를 호출하여 최종 텍스트 답변 획득
        try {
            return chatModel.generate(systemPrompt);
        } catch (Exception e) {
            System.err.println(" !#@$ AI 응답 생성 중 오류 발생: " + e.getMessage());
            return "서버 내부 오류로 인해 답변을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        }
    }
}
