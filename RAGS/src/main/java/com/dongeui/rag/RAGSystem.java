package com.dongeui.rag;

import com.dongeui.rag.config.AppConfig;
import com.dongeui.rag.crawler.WebCrawler;
import com.dongeui.rag.model.KnowledgeCompiler;
import com.dongeui.rag.parser.PdfMarkdownParser;
import com.dongeui.rag.repository.VectorKnowledgeStore;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

public class RAGSystem {

    public static void main(String[] args)
            throws Exception {

        /*
         * =========================
         * UTF-8 설정
         * =========================
         */

        try {

            System.setOut(
                    new java.io.PrintStream(
                            System.out,
                            true,
                            "UTF-8"
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();
        }

        System.setProperty(
                "file.encoding",
                "UTF-8"
        );

        /*
         * =========================
         * 설정 로드
         * =========================
         */

        AppConfig config =
                new AppConfig();

        /*
         * =========================
         * 벡터 저장소
         * =========================
         */

        VectorKnowledgeStore store =
                new VectorKnowledgeStore(
                        config.getEmbeddingModel(),
                        config.getChatModel()
                );

        /*
         * =========================
         * AI Markdown 컴파일러
         * =========================
         */

        KnowledgeCompiler compiler =
                new KnowledgeCompiler(
                        config.getChatModel()
                );

        /*
         * =========================
         * PDF 파서
         * =========================
         */

        PdfMarkdownParser parser =
        new PdfMarkdownParser(
                compiler
        );

        /*
         * =========================
         * PDF 캐시 폴더
         * =========================
         */

        File pdfCacheDir =
                new File("./pdf_cache");

        if (!pdfCacheDir.exists()) {

            pdfCacheDir.mkdirs();
        }

        /*
         * =========================
         * PDF 처리
         * =========================
         */

        /*
 * =========================
 * PDF 처리
 * =========================
 */

System.out.println(
        "\n📄 PDF 분석 시작...\n"
);

File docsDir =
        new File("./docs");

if (!docsDir.exists()) {

    docsDir.mkdirs();
}

File[] pdfFiles =
        docsDir.listFiles();

if (pdfFiles != null) {

    for (File pdf : pdfFiles) {

        if (!pdf.getName()
                .toLowerCase()
                .endsWith(".pdf")) {

            continue;
        }

        /*
         * 파일명 정리
         */
        String baseName =
                pdf.getName()
                        .replace(".pdf", "")
                        .replaceAll("[\\\\/:*?\"<>|]", "")
                        .replaceAll("\\s+", "_")
                        .trim();

        /*
         * 캐시 파일
         */
        File cacheFile =
                new File(
                        pdfCacheDir,
                        baseName + ".md"
                );

        String markdown;

        /*
         * =========================
         * PDF 캐시 존재
         * =========================
         */

        if (cacheFile.exists()) {

            System.out.println(
                    "📂 PDF 캐시 로드: "
                            + cacheFile.getName()
            );

            markdown =
                    Files.readString(
                            cacheFile.toPath(),
                            StandardCharsets.UTF_8
                    );
        }

        /*
         * =========================
         * 신규 PDF 분석
         * =========================
         */

        else {

            System.out.println(
                    "📄 신규 PDF 분석: "
                            + pdf.getName()
            );

            markdown =
                    parser.parse(pdf);

            Files.writeString(
                    cacheFile.toPath(),
                    markdown,
                    StandardCharsets.UTF_8
            );

            System.out.println(
                    "✅ PDF 캐시 저장 완료"
            );
        }

        /*
         * =========================
         * 벡터 저장
         * =========================
         */

        store.saveToVectorStore(
                markdown,
                baseName,
                "none",
                "none"
        );

        System.out.println(
                "📦 PDF 벡터 저장 완료: "
                        + baseName
        );
    }
}

        /*
         * =========================
         * 웹 캐시 폴더
         * =========================
         */

        File webCacheDir =
                new File("./web_cache");

        if (!webCacheDir.exists()) {

            webCacheDir.mkdirs();
        }

        /*
         * =========================
         * 웹 크롤링
         * =========================
         */

        System.out.println(
                "\n🌐 웹 크롤링 시작...\n"
        );

        /*
         * 최초 실행 시 크롤링
         */

        if (webCacheDir.listFiles() == null
                || webCacheDir.listFiles().length == 0) {

            WebCrawler crawler =
                    new WebCrawler(
                            compiler,
                            store
                    );

            crawler.crawlAndIndexWeb(
                    "https://deuhome.deu.ac.kr/se/index.do"
            );
        }

        /*
         * 기존 웹 캐시 로드
         */

        else {

            System.out.println(
                    "📂 기존 웹 캐시 로드 중...\n"
            );

            File[] cachedFiles =
                    webCacheDir.listFiles();

            if (cachedFiles != null) {

                for (File file : cachedFiles) {

                    if (!file.getName()
                            .endsWith(".md")) {

                        continue;
                    }

                    String markdown =
                            Files.readString(
                                    file.toPath(),
                                    StandardCharsets.UTF_8
                            );

                    store.saveToVectorStore(
                            markdown,
                            file.getName(),
                            "none",
                            "none"
                    );

                    System.out.println(
                            "✅ 웹 캐시 로드: "
                                    + file.getName()
                    );
                }
            }
        }

        /*
         * =========================
         * 질문 루프
         * =========================
         */

        System.out.println(
                "\n🤖 RAG 시스템 준비 완료"
        );

        System.out.println(
                "종료하려면 exit 입력\n"
        );

        Scanner scanner =
                new Scanner(System.in);

        while (true) {

            System.out.print("질문 > ");

            String question =
                    scanner.nextLine();

            if (question == null
                    || question.isBlank()) {

                continue;
            }

            if (question.equalsIgnoreCase("exit")) {

                break;
            }

            try {

                String answer =
                        store.askQuestion(question);

                System.out.println("\n");
                System.out.println(answer);
                System.out.println("\n");

            } catch (Exception e) {

                System.out.println(
                        "❌ 질문 처리 실패: "
                                + e.getMessage()
                );
            }
        }

        scanner.close();

        System.out.println(
                "\n👋 시스템 종료"
        );
    }
}