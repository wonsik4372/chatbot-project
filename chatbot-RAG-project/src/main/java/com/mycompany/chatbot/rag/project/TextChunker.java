package com.mycompany.chatbot.rag.project;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * 텍스트를 청크 크기 1000자, 오버랩 200자로 분할합니다.
 *
 * LangChain4j의 DocumentByCharacterSplitter를 사용하여 maxSegmentSize=1000,
 * maxOverlapSize=200 으로 설정합니다.
 */
public class TextChunker {

    private static final int CHUNK_SIZE = 500; // 청크 최대 글자 수
    private static final int OVERLAP_SIZE = 100; // 이전 청크와 겹치는 글자 수

    private final DocumentSplitter splitter
            = new DocumentByCharacterSplitter(CHUNK_SIZE, OVERLAP_SIZE);

    /**
     * 원본 문서 텍스트를 오버랩 청크 목록으로 분할합니다.
     *
     * @param source 출처 식별자 (파일명 또는 URL)
     * @param text 원본 전체 텍스트
     * @return 분할된 TextSegment 목록 (각 세그먼트에 source 메타데이터 포함)
     */
    public List<TextSegment> chunk(String source, String text) {
        Metadata metadata = Metadata.from("source", source);

        Document doc = Document.from(text, metadata);

        List<TextSegment> segments = splitter.split(doc);

        System.out.println("  [청킹] " + source + " → "
                + segments.size() + "개 청크 생성");

        return segments;
    }

    /**
     * 여러 RawDocument를 일괄 청킹합니다.
     */
    public List<TextSegment> chunkAll(List<DocumentLoader.RawDocument> docs) {
        List<TextSegment> all = new ArrayList<>();
        for (DocumentLoader.RawDocument doc : docs) {
            all.addAll(chunk(doc.source(), doc.text()));
        }
        return all;
    }
}
