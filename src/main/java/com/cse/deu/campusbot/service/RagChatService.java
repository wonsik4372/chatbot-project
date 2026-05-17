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
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

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

    // 정확도를 위해 추출할 최대 문맥 개수
    private static final int MAX_RESULTS = 7;
    
    // 최소 유사도 점수 (0.0 ~ 1.0)
    private static final double MIN_SCORE = 0.8;

    public RagChatService(ChatLanguageModel chatModel, 
                          EmbeddingModel embeddingModel, 
                          EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public String askQuestion(String query) {
        System.out.println("\n[진행 알림] 사용자 질문 수신: " + query);
        
        String roomNumber = "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{3})호?").matcher(query);
        if (matcher.find()) {
            roomNumber = matcher.group(1); 
        }

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(MAX_RESULTS)
                .minScore(MIN_SCORE);

        if (!roomNumber.isEmpty()) {
            System.out.println("🎯 [필터링 작동] " + roomNumber + "호 데이터만 집중 검색합니다.");
            requestBuilder.filter(
                MetadataFilterBuilder.metadataKey("source").isIn(roomNumber + ".pdf", 
                                                                 roomNumber + ".csv",
                                                                 roomNumber + ".md")
                        
            );
        }

        EmbeddingSearchRequest searchRequest = requestBuilder.build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
        
        System.out.println("\n📊 [검색된 청크 유사도 결과 Top " + matches.size() + "]");
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            double score = match.score(); 
            String source = match.embedded().metadata().getString("source");
            String textSnippet = match.embedded().text().replaceAll("\\n", " ");
            if (textSnippet.length() > 50) {
                textSnippet = textSnippet.substring(0, 50) + "...";
            }
            System.out.printf("   👉 [%d위] 점수: %.4f | 출처: %s | 내용: %s\n", 
                              i + 1, score, source, textSnippet);
        }
        System.out.println("=======================================================\n");
    
        if (matches.isEmpty()) {
            System.out.println("⚠️ 관련 문맥을 찾을 수 없습니다.");
            return "죄송합니다. 현재 학습된 학교 데이터 중에서는 해당 질문에 대한 정보를 찾을 수 없습니다.";
        }

        String context = matches.stream()
                .map(match -> {
                    String source = match.embedded().metadata().getString("source");
                    return "[" + source + "]\n" + match.embedded().text();
                })
                .collect(Collectors.joining("\n\n"));

        System.out.println("🔍 [디버깅] AI에게 전달될 문맥 데이터 개수: " + matches.size() + "개");

        String systemPrompt = String.format(
            "### [필독] 절대적 제약 규칙 (CRITICAL SYSTEM RULES)\n" +
            "1. 환각 효과 금지: 제공된 [참고 데이터]에 없는 사실을 유추하거나, 상상하거나, 임의로 지어내어 답변하지 마라. 데이터가 부족하면 부족하다고 사실대로만 말해.\n" +
            "2. 질문 유도 금지: 답변 끝에 \"추가로 궁금한 점이 있으신가요?\", \"~에 대해 다시 요청해주시면 정리해 드리겠습니다\"와 같이 사용자에게 추가 질문을 유도하거나 가이드하는 문장을 절대 포함하지 마라. 답변은 명확한 서술형 종결어미(. )로 완전히 끝맺어.\n\n" +"### 역할\n" +
            "너는 컴퓨터소프트웨어공학과 학사 정보 및 학과 안내를 담당하는 지능형 어시스턴트야.\n\n" +
            "### 임무\n" +
            "제공된 [참고 데이터]를 바탕으로 학생의 질문에 정확하게 답변해.\n\n" +
            "### 데이터 분석 가이드\n" +
            "1. 시간표(CSV) 데이터일 경우: 요일과 교시, 강의실(예: [문서명:911])을 파악해서 답변해. 데이터에 띄어쓰기가 없더라도 유추해서 읽어내.\n" +
            "2. 일반 학과 정보(마크다운/웹크롤링)일 경우: 내용을 꼼꼼히 읽고 질문에 맞는 핵심 정보를 요약해서 제공해.\n\n" +
            "### 답변 원칙\n" +
            "- [참고 데이터]에 있는 정보만 사용해. 데이터에 없으면 지어내지 마.\n" +
            "- 마크다운 표(Table) 형식은 절대 사용하지 마.\n" +
            "- 이모지와 불릿 리스트(-)를 적극 활용하여 가독성 좋게 시각화해줘.\n\n" +
            "- 추가적인 질문이나 궁금한 점을 사용자에게 유도하지마." +
            "### 참고 데이터\n%s\n\n" +
            "### 학생 질문\n%s\n\n" +
            "답변:",
                
            context, query
        );

        System.out.println("[진행 알림] AI가 답변을 생성 중입니다...");

        try {
            return chatModel.generate(systemPrompt);
        } catch (Exception e) {
            System.err.println(" !#@$ AI 응답 생성 중 오류 발생: " + e.getMessage());
            return "서버 내부 오류로 인해 답변을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        }       
    }
    
    public String compileWebContent(String rawContent, String sourceName) {
        System.out.println("✨ LLM이 [" + sourceName + "] 웹 크롤링 데이터를 RAG 최적화 마크다운으로 컴파일 중...");
        
        // 웹 텍스트 내 '%' 기호에 의한 예외 방지 및 동적 제목 할당
        String prompt = "너는 RAG 검색용 지식 정제 에이전트야. 제공된 웹사이트 텍스트를 마크다운 리스트 형태로 구조화해.\n" +
                        "인사말, 반복되는 사이트 메뉴명, 사족은 절대 포함하지 마.\n\n" +
                        "🧱 [작성 규칙]\n" +
                        "1. 문서 최상단에 이 문서의 주제와 관련된 '검색용 핵심 키워드 태그'를 대량으로 배치해라.\n" +
                        "2.   질문과 비교할 땐 #으로된 태그를 중점적으로 보고 비교해.\n" +
                        "3. 내용을 요약하여 생략하지 말고, 인물이나 항목이 등장하면 이름, 연구실, 연락처를 불릿 포인트로 모두 나열해라.\n\n" +

                        "🧱 [출력 포맷 스키마]\n" +
                        "## 🌐 학과 홈페이지 지식 베이스: [" + sourceName + "]\n" +
                        "* **검색 태그:** #교수명단 #교수진 #전체교수 #교수소개 #연락처 #연구실 #컴퓨터소프트웨어학과\n" +
                        "### 📌 [주요 내용 및 리스트]\n" +
                        "(이 아래로 핵심 내용만 불릿 포인트나 간결한 문장으로 요약해서 출력)"+
                        "[원본 텍스트]\n" +
                        rawContent;
        
            /*
            "🧱 [작성 규칙]\n" +
            "1. 문서 최상단에 이 문서의 주제와 관련된 '검색용 핵심 키워드 태그'를 대량으로 배치해라.\n" +
            "   (예: 만약 교수 소개 페이지라면 #교수명단 #교수진 #교수님 #담당교수 #컴소교수 #교수소개 등을 무조건 포함)\n" +
            "2. 내용을 요약하여 생략하지 말고, 인물이나 항목이 등장하면 이름, 연구실, 연락처를 불릿 포인트로 모두 나열해라.\n\n" +
            
            "🧱 [출력 포맷 스키마]\n" +
            "## 🌐 학과 홈페이지 지식 베이스: [" + sourceName + "]\n" +
            "* **검색 태그:** #교수명단 #교수진 #전체교수 #교수소개 #연락처 #연구실 #컴퓨터소프트웨어학과\n" +
            "### 📌 [주요 내용 및 리스트]\n" +
            "[입력 데이터의 내용을 기반으로 가독성 있게 마크다운화]\n\n" +
            "--- \n\n" +
            "[입력 데이터]\n" +
            rawContent + "\n\n" +
            "마크다운 결과 >";
            */

        return executeQuery(prompt, rawContent);
    }

    private String executeQuery(String prompt, String fallback) {
        try {
            return this.chatModel.generate(prompt);
        } catch (Exception e) {
            System.err.println("⚠️ LLM 컴파일 프롬프트 전송 실패로 폴백 처리합니다: " + e.getMessage());
            return fallback;
        }
    }
}