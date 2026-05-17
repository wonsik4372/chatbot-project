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
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    public void saveToVectorStore(String text, String source, String room, String professor) {
        if (text == null || text.trim().isEmpty()) return;

        Metadata metadata = new Metadata()
                .add("source", source)
                .add("room", room != null ? room : "none")
                .add("professor", professor != null ? professor : "none");

        List<TextSegment> segments = new ArrayList<>();

        if (source.contains("sub02") || source.contains("faculty") || text.contains("교수진 소개")) {
            System.out.println("✂️ [정밀 정형 인덱싱] '" + source + "' 교수 안내 문서를 분할합니다.");
            
            // AI가 마크다운을 렌더링할 때 '###'이 아니라 '##'이나 '-'로 그렸을 수도 있으므로,
            // 분할 기준을 조금 더 유연하게 가져가거나 안전한 재귀 분할기를 병행 사용하는 것이 좋습니다.
            if (text.contains("###")) {
                String[] parts = text.split("###");
                String headerInfo = parts[0]; 
                for (int i = 1; i < parts.length; i++) {
                    String professorBlock = parts[i].trim();
                    if (professorBlock.isEmpty()) continue;
                    String fullBlockText = "## 컴퓨터소프트웨어학과 교수 소개\n\n### " + professorBlock;
                    segments.add(TextSegment.from(fullBlockText, metadata));
                }
                if (!headerInfo.trim().isEmpty()) {
                    segments.add(TextSegment.from(headerInfo, metadata));
                }
            } else {
                // 마크다운 구조가 다를 경우를 대비한 안전망 (일반 분할)
                Document document = Document.from(text, metadata);
                segments = DocumentSplitters.recursive(800, 150).split(document);
            }
        } else {
            Document document = Document.from(text, metadata);
            segments = DocumentSplitters.recursive(800, 150).split(document);
        }

        for (TextSegment segment : segments) {
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    public String askQuestion(String query) {
        System.out.println("🌐 [고정밀 벡터 검색] '" + query + "' 질의에 대한 유사도 탐색을 시작합니다.");
        
        // 💡 [핵심 수정] 
        // 하드코딩된 Metadata Filter(roomFilter)를 삭제했습니다. 
        // 텍스트 내부에 이미 "912호"라는 텍스트가 있으므로 임베딩(유사도) 검색만으로 충분히 912호 시간표를 찾아냅니다.
        // 필터가 있으면 저장될 때 메타데이터가 꼬였을 경우 데이터를 영영 찾지 못합니다.
        
        EmbeddingSearchRequest searchRequest = new EmbeddingSearchRequest(
                embeddingModel.embed(query).content(),
                15,                                    // 넉넉하게 15개 청크 수집
                0.20,                                  // 커트라인 낮춰서 유연한 검색
                null                                   // 강제 필터 해제
        );

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
            return "현재 학과 데이터베이스에서 관련 문서를 찾을 수 없습니다.";
        }

        // 💡 [핵심 수정] AI의 "환각(Hallucination)"을 원천 차단하는 초강력 방어 프롬프트
        String prompt = String.format(
            "당신은 동의대학교 컴퓨터소프트웨어공학과 학사 정보 전문 비서 AI입니다.\n" +
            "⚠️ [가장 중요한 절대 규칙]: 당신은 당신이 기존에 학습한 인터넷 지식(예: 2024년 기준 등)을 절대 사용해서는 안 됩니다.\n" +
            "오직 아래 제공된 [학과 지식 컨텍스트]의 내용 안에서만 정답을 찾아서 답변하세요.\n\n" +
            
            "1. 만약 사용자가 물어본 인물(예: 장희숙 교수 등)이나 정보가 [학과 지식 컨텍스트]에 존재하지 않는다면, 절대 지어내지 말고 '현재 수집된 학과 데이터베이스에는 해당 정보가 없습니다.'라고 명확하게 단답형으로 답변하세요.\n" +
            "2. 교수 명단이나 시간표를 요구하면, 컨텍스트 파편에 흩어져 있는 이름과 과목을 악착같이 긁어모아 사용자가 보기 편하게 리스트나 마크다운 표로 깔끔하게 정리해 주세요.\n\n" +
            
            "[학과 지식 컨텍스트 (이 정보만 사용할 것!)]\n%s\n\n" +
            "[사용자 질문]\n%s\n\n" +
            "📚 답변 >", context, query);

        return chatModel.generate(prompt);
    }
}