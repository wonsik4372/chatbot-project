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
    private static final int MAX_RESULTS = 10;
    
    // 최소 유사도 점수 (0.0 ~ 1.0)
    // 이 점수보다 낮은 관련도의 데이터 무시
    private static final double MIN_SCORE = 0.6;

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

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();

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
                .collect(Collectors.joining("\n\n---\n\n"));

        System.out.println("🔍 [디버깅] AI에게 전달될 문맥 데이터 개수: " + matches.size() + "개");

        // 프롬프트(Prompt) 엔지니어링 
        String systemPrompt = String.format(
            "당신은 대학교 행정 및 학사 일정을 안내하는 친절하고 정확한 비서 AI입니다.\n" +
            "아래 제공된 [참고 데이터]만을 바탕으로 사용자의 [질문]에 답변하세요.\n\n" +
            "🚨 [절대 지켜야 할 주의사항]\n" +
            "1. 참고 데이터에 없는 내용은 절대 지어내지(Hallucination) 마세요.\n" +
            "2. 모르는 내용이면 '정보가 없습니다'라고 솔직하게 답변하세요.\n" +
            "3. 답변은 간결하고 가독성 좋게 마크다운(Markdown) 리스트나 표를 활용해 정리해 주세요.\n\n" +
            "=== [참고 데이터 시작] ===\n%s\n=== [참고 데이터 끝] ===\n\n" +
            "👤 [질문]: %s\n" +
            "🤖 [답변]:", 
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
