package com.dongeui.rag.repository;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import dev.langchain4j.model.embedding.EmbeddingModel;

import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class WebKnowledgeStore {

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> store;

    private final List<TextSegment> cachedSegments =
            new ArrayList<>();

    public WebKnowledgeStore(
            EmbeddingModel embeddingModel
    ) {

        this.embeddingModel = embeddingModel;

        this.store =
                new InMemoryEmbeddingStore<>();
    }

    /**
     * 웹 문서 저장
     */
    public void saveWebDocument(
            String title,
            String url,
            String content
    ) {

        try {

            if (content == null
                    || content.isBlank()) {
                return;
            }

            // 저장 폴더 생성
            File dir = new File("./web_cache");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 파일명 안전화
            String safeTitle = title
                    .replaceAll("[\\\\/:*?\"<>|]", "_")
                    .trim();

            if (safeTitle.isBlank()) {
                safeTitle = "web_document";
            }

            File file = new File(
                    dir,
                    safeTitle + ".txt"
            );

            // txt 저장
            try (FileWriter writer =
                         new FileWriter(
                                 file,
                                 StandardCharsets.UTF_8
                         )) {

                writer.write("[TITLE]\n");
                writer.write(title + "\n\n");

                writer.write("[URL]\n");
                writer.write(url + "\n\n");

                writer.write("[CONTENT]\n");
                writer.write(content);
            }

            // category 자동 분류
            String category = "general";

            if (url.contains("prof")
                    || url.contains("faculty")
                    || url.contains("teacher")
                    || title.contains("교수")
                    || content.contains("교수")) {

                category = "professor";
            }

            // chunk 분리
            List<String> chunks =
                    splitContent(content);

            for (String chunk : chunks) {

                Metadata metadata =
                        new Metadata()
                                .add("source", title)
                                .add("url", url)
                                .add("type", "web")
                                .add("category", category);

                TextSegment segment =
                        TextSegment.from(
                                chunk,
                                metadata
                        );

                Embedding embedding =
                        embeddingModel
                                .embed(segment)
                                .content();

                store.add(embedding, segment);

                cachedSegments.add(segment);
            }

            System.out.println(
                    "🌐 웹 문서 저장 완료: "
                            + title
            );

            System.out.println(
                    "📄 저장 위치: "
                            + file.getAbsolutePath()
            );

        } catch (Exception e) {

            System.out.println(
                    "❌ 웹 저장 실패: "
                            + e.getMessage()
            );
        }
    }

    /**
     * 내용 chunk 분리
     */
    private List<String> splitContent(
            String content
    ) {

        List<String> chunks =
                new ArrayList<>();

        String[] paragraphs =
                content.split("\n");

        StringBuilder current =
                new StringBuilder();

        for (String para : paragraphs) {

            para = para.trim();

            if (para.isBlank()) {
                continue;
            }

            current.append(para)
                    .append("\n");

            // chunk 크기
            if (current.length() >= 700) {

                chunks.add(
                        current.toString()
                );

                current = new StringBuilder();
            }
        }

        if (!current.isEmpty()) {

            chunks.add(current.toString());
        }

        return chunks;
    }

    /**
     * 저장된 웹 문서 출력
     */
    public void printAllDocuments() {

        System.out.println(
                "\n===== 웹 문서 목록 ====="
        );

        for (TextSegment seg : cachedSegments) {

            System.out.println(
                    seg.text()
            );

            System.out.println(
                    "----------------------"
            );
        }
    }

    /**
     * 저장 개수
     */
    public int size() {

        return cachedSegments.size();
    }

    /**
     * txt 읽기
     */
    public String readTextFile(
            File file
    ) {

        try {

            return Files.readString(
                    file.toPath(),
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            return "";
        }
    }
}