package com.dongeui.rag.repository;

import com.dongeui.rag.config.AppConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata; 
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public class VectorKnowledgeStore {
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public VectorKnowledgeStore(EmbeddingModel embeddingModel, ChatLanguageModel chatModel) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.embeddingStore = new InMemoryEmbeddingStore<>();

        File cacheDirFile = new File(AppConfig.CACHE_DIR);
        if (!cacheDirFile.exists()) {
            cacheDirFile.mkdirs();
        }
    }

    /**
     * [🎯 최종 최적화] 교수진 안내 페이지는 "교수님 한 분 단위"로 정밀 정형 청킹을 수행합니다.
     */
    public void saveToVectorStore(String text, String source, String room, String professor) {
        if (text == null || text.trim().isEmpty()) return;

        Metadata metadata = new Metadata()
                .add("source", source)
                .add("room", room)
                .add("professor", professor);

        List<TextSegment> segments = new ArrayList<>();

        // ✨ [핵심 교정] 교수진 소개 문서(sub02)일 경우, 마크다운의 교수님 구분선인 '###' 기준으로 잘게 쪼갭니다.
        if (source.contains("sub02") || source.contains("faculty") || text.contains("교수진 소개")) {
            System.out.println("✂️ [정밀 정형 인덱싱] '" + source + "' 교수 안내 문서를 인명별 독립 청크로 정밀 분할합니다.");
            
            // ### 기준으로 자르면 교수님 한 분씩 데이터가 분리됨
            String[] parts = text.split("###");
            String headerInfo = parts[0]; // 상단 학과 설명글
            
            for (int i = 1; i < parts.length; i++) {
                String professorBlock = parts[i].trim();
                if (professorBlock.isEmpty()) continue;
                
                // 각 교수님 블록마다 독립적인 텍스트 세그먼트 생성 (정보 유실 원천 차단 및 밀도 극대화)
                String fullBlockText = "## 컴퓨터소프트웨어학과 교수 소개\n\n### " + professorBlock;
                segments.add(TextSegment.from(fullBlockText, metadata));
            }
            
            // 상단 개요도 청크에 추가
            if (!headerInfo.trim().isEmpty()) {
                segments.add(TextSegment.from(headerInfo, metadata));
            }
        } else {
            // 시간표나 일반 학사 정보 웹 문서는 기존 계층 구조 분할기 유지
            Document document = Document.from(text, metadata);
            segments = DocumentSplitters.recursive(800, 150).split(document);
        }

        // 벡터 저장소에 정교화된 청크 배치 임베딩 주입
        for (TextSegment segment : segments) {
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    public String askQuestion(String query) {
        String roomFilterTag = "none";
        
        if (query.contains("호") || query.contains("실습실") || query.contains("강의실") || query.contains("강의실별")) {
            String cleanQuery = query.replaceAll("[^0-9]", "");
            if (cleanQuery.length() >= 3) {
                roomFilterTag = cleanQuery.substring(0, 3);
            }
        }

        EmbeddingSearchRequest searchRequest;

        if (!roomFilterTag.equals("none")) {
            System.out.println("🎯 [시스템 필터 동작] 강의실 '" + roomFilterTag + "호' 전용 메타데이터 필터링 활성화.");
            Filter roomFilter = metadataKey("room").isEqualTo(roomFilterTag);
            
            searchRequest = new EmbeddingSearchRequest(
                    embeddingModel.embed(query).content(),
                    8,                                     // 강의실 기반 질의 시 교수 정보 누락 방지를 위해 8개로 상향
                    0.25,                                  // minScore 완화
                    roomFilter
            );
        } else {
            System.out.println("🌐 [고정밀 벡터 검색] 전체 지식 베이스 검색 범위를 확장하여 심층 탐색을 수행합니다.");
            
            searchRequest = new EmbeddingSearchRequest(
                    embeddingModel.embed(query).content(),
                    15,                                    // maxResults를 15개 청크로 확장 (분할된 교수 명단 전원 확보)
                    0.20,                                  // 최저 커트라인을 0.20까지 더 낮춰 유사 토큰 무조건 포획
                    null
            );
        }

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
        
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            contextBuilder.append("[참고 자료 ").append(i + 1).append(" - 출처: ")
                          .append(match.embedded().metadata().getString("source")).append("]\n")
                          .append(match.embedded().text()).append("\n\n");
        }
        
        String context = contextBuilder.toString();

        if (context.trim().isEmpty()) {
            return "보유하고 있는 학과 지식 베이스에서 해당 질문에 대한 정보를 찾을 수 없습니다.";
        }

        // LLM이 헛소리를 하거나 발뺌하지 못하도록 최종 방어벽을 세운 프롬프트
        String prompt = String.format(
            "당신은 동의대학교 컴퓨터소프트웨어공학과 학사 정보 전문 비서 AI입니다.\n" +
            "반드시 아래 제공된 [학과 지식 컨텍스트]의 내용을 완벽하게 숙지하고, 여기에 등장하는 정보를 기반으로만 답변하세요.\n" +
            "교수님들의 이름(예: 권오준, 권순각, 김성우, 이중화 등), 연구실호수, 연락처, 이메일이 컨텍스트 파편에 흩어져 있습니다.\n" +
            "이 정보들을 악착같이 긁어모아 사용자가 보기 편하게 리스트(표 또는 글머리 기호)로 단 하나도 빠짐없이 전부 나열하여 친절하게 답변하세요.\n" +
            "컨텍스트에 정보가 엄연히 존재함에도 '정보가 없다'거나 '학과 홈피를 보라'는 식으로 책임을 회피하면 시스템 에러가 발생합니다.\n\n" +
            "[학과 지식 컨텍스트]\n%s\n" +
            "[사용자 질문]\n%s\n\n" +
            "📚 답변 >", context, query);

        return chatModel.generate(prompt);
    }
}