/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.InputStream;
import java.util.List;

/**
 *
 * @author wonsik
 */
public class TabulaPdfParser implements DocumentParser {

    @Override
    public Document parse(InputStream inputStream) {
        StringBuilder csvBuilder = new StringBuilder();

        try (PDDocument pdDocument = PDDocument.load(inputStream)) {
            ObjectExtractor extractor = new ObjectExtractor(pdDocument);
            
            // 글자 간격이 아닌 '표의 테두리 선'을 기준으로 셀을 나누는 알고리즘
            SpreadsheetExtractionAlgorithm algorithm = new SpreadsheetExtractionAlgorithm();
            PageIterator pages = extractor.extract();

            while (pages.hasNext()) {
                Page page = pages.next();
                List<Table> tables = algorithm.extract(page);
                
                for (Table table : tables) {
                    for (List<RectangularTextContainer> row : table.getRows()) {
                        for (int i = 0; i < row.size(); i++) {
                            // 셀 안의 텍스트 가져오기
                            String text = row.get(i).getText();
                            
                            // 쓸데없는 캐리지 리턴(\r) 제거하되, 줄바꿈(\n)은 유지 (과목명과 교수명 분리용)
                            text = text.replace("\r", " ")
                                       .replace("\n", " ")
                                       .replaceAll("\\s+", " ")
                                       .trim();
                            
                            // CSV 표준 포맷팅 (쉼표, 줄바꿈, 따옴표가 있으면 전체를 큰따옴표로 감싸기)
                            if (text.contains(",") || text.contains("\"")) {
                                text = "\"" + text.replace("\"", "\"\"") + "\"";
                            }
                            else if (text.contains("\n")) {
                               text = "\"" + text.replace("\"", "\"\"") + "\"";    
                            }
                            
                            csvBuilder.append(text);
                            
                            // 마지막 열이 아니면 쉼표(,) 추가
                            if (i < row.size() - 1) {
                                csvBuilder.append(",");
                            }
                        }
                        csvBuilder.append("\n"); // 한 줄 끝날 때 줄바꿈
                    }
                    csvBuilder.append("\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF -> CSV 구조 변환 중 오류 발생", e);
        }

        return Document.from(csvBuilder.toString());
    }
}