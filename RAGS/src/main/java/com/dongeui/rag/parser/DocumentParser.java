package com.dongeui.rag.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import java.io.FileInputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;

import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.extractors.BasicExtractionAlgorithm;

public class DocumentParser {

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    public String parseFile(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            return extractPdfUsingTabula(file);
        } else if (name.endsWith(".html") || name.endsWith(".htm")) {
            return parseHtmlText(file);
        } else if (name.endsWith(".txt") || name.endsWith(".csv")) {
            return Files.readString(file.toPath());
        } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return parseExcel(file);
        }
        return "";
    }

    private String parseExcel(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    sb.append(cell.toString()).append("\t");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * [Step 2-1] PDF 상단 핵심 메타데이터(강의실, 학기)를 포함한 무유실 오리지널 CSV 추출
     */
    public String extractPdfToPureCsv(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        
        try (PDDocument document = PDDocument.load(file)) {
            // 1. 최상단 타이틀 및 강의실 정보 추출
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String fullText = stripper.getText(document);
            
            if (fullText != null && !fullText.trim().isEmpty()) {
                sb.append("# === PDF DOCUMENT METADATA ===\n");
                String[] lines = fullText.split("\\r?\\n");
                int metaCount = 0;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.contains("Ucb0309r") && metaCount < 5) {
                        sb.append("# ").append(trimmed).append("\n");
                        metaCount++;
                    }
                }
                sb.append("# =============================\n\n");
            }

            // 2. Tabula 격자 추출
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm spreadsheetAlgo = new SpreadsheetExtractionAlgorithm();
            BasicExtractionAlgorithm basicAlgo = new BasicExtractionAlgorithm();
            PageIterator pages = oe.extract();

            while (pages.hasNext()) {
                Page page = pages.next();
                List<Table> tables = (List<Table>) spreadsheetAlgo.extract(page);
                if (tables == null || tables.isEmpty()) {
                    tables = (List<Table>) basicAlgo.extract(page);
                }

                if (tables != null) {
                    for (Table table : tables) {
                        for (List<RectangularTextContainer> row : table.getRows()) {
                            StringBuilder rowBuilder = new StringBuilder();
                            for (int i = 0; i < row.size(); i++) {
                                String cellText = row.get(i).getText();
                                cellText = (cellText != null) ? cellText.replaceAll("[\r\n]", " ").replaceAll("\\s+", " ").trim() : "";
                                rowBuilder.append("\"").append(cellText.replace("\"", "\"\"")).append("\"");
                                if (i < row.size() - 1) rowBuilder.append(",");
                            }
                            sb.append(rowBuilder.toString()).append("\n");
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * [Step 2-2] 상단 메타데이터를 보존하여 요일 인덱스가 완전히 일치하도록 1차원 정제 스트림화
     */
    private String extractPdfUsingTabula(File file) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String rawFullText = stripper.getText(document);
            
            // 스트림 최상단에 메타데이터 우선 주입하여 LLM 탈선 예방
            if (rawFullText != null && !rawFullText.trim().isEmpty()) {
                sb.append("[TARGET METADATA]\n");
                String[] lines = rawFullText.split("\\r?\\n");
                int extractedLines = 0;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.contains("Ucb0309r") && extractedLines < 4) {
                        sb.append("CONTEXT: ").append(trimmed).append("\n");
                        extractedLines++;
                    }
                }
                sb.append("[END OF TARGET METADATA]\n\n");
            }

            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm spreadsheetAlgo = new SpreadsheetExtractionAlgorithm();
            BasicExtractionAlgorithm basicAlgo = new BasicExtractionAlgorithm();
            PageIterator pages = oe.extract();

            String[] weekdays = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};

            sb.append("=== [PRE-PROCESSED TIMETABLE STREAM] ===\n");
            while (pages.hasNext()) {
                Page page = pages.next();
                List<Table> tables = (List<Table>) spreadsheetAlgo.extract(page);

                if (tables == null || tables.isEmpty()) {
                    tables = (List<Table>) basicAlgo.extract(page);
                }

                if (tables != null) {
                    for (Table table : tables) {
                        for (List<RectangularTextContainer> row : table.getRows()) {
                            if (row.size() < 7) continue;

                            String timeRow = row.get(0).getText();
                            if (timeRow == null || timeRow.contains("구분")) continue;
                            
                            timeRow = timeRow.replaceAll("[\\r\\n]", " ").replaceAll("\\s+", " ").trim();

                            for (int i = 1; i <= 6; i++) {
                                RectangularTextContainer cell = row.get(i);
                                String cellText = cell.getText();
                                
                                if (cellText != null) {
                                    cellText = cellText.replaceAll("[\\r\\n]", " ").replaceAll("\\s+", " ").trim();
                                } else {
                                    cellText = "";
                                }

                                if (!cellText.isEmpty() && !cellText.equals("\"\"")) {
                                    String weekday = weekdays[i - 1];
                                    sb.append(String.format("[%s] %s -> %s\n", weekday, timeRow, cellText));
                                }
                            }
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }
    
    private String parseHtmlText(File file) throws IOException {
        String html = Files.readString(file.toPath());
        String cleanText = html.replaceAll("(?s)<script.*?>.*?</script>", "")
                               .replaceAll("(?s)<style.*?>.*?</style>", "");
        
        cleanText = HTML_TAGS.matcher(cleanText).replaceAll(" ");
        cleanText = MULTIPLE_SPACES.matcher(cleanText).replaceAll(" ").trim();
        
        return "=== [웹 크롤링 페이지 정제 스트림] ===\n" + cleanText;
    }
}