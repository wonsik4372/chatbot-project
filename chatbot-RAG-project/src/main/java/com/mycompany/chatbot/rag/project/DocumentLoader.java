package com.mycompany.chatbot.rag.project;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DocumentLoader {

    private final TimetableStore timetableStore;

    public DocumentLoader(TimetableStore timetableStore) {
        this.timetableStore = timetableStore;
    }

    public List<RawDocument> loadDirectory(String directoryPath) throws IOException {
        File dir = new File(directoryPath);

        if (!dir.isDirectory()) {
            throw new IOException("유효한 디렉토리가 아닙니다: " + directoryPath);
        }

        List<RawDocument> docs = new ArrayList<>();
        File[] files = dir.listFiles();

        if (files == null) {
            return docs;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();

            try {
                if (isTimetablePdf(name)) {
                    parseTimetablePdf(file);
                    System.out.println("  [시간표 저장] " + name);
                    continue;
                }

                String text = null;

                if (name.endsWith(".txt")) {
                    text = loadTxt(file);
                } else if (name.endsWith(".pdf")) {
                    text = loadPdf(file);
                } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                    text = loadXlsx(file);
                }

                if (text != null && !text.isBlank()) {
                    docs.add(new RawDocument(name, text));
                    System.out.println("  [로드 완료] " + name);
                }

            } catch (Exception e) {
                System.err.println("  [로드 실패] " + name + ": " + e.getMessage());
            }
        }

        return docs;
    }

    private boolean isTimetablePdf(String filename) {
        return filename.endsWith(".pdf")
                && (filename.contains("교수님")
                || filename.startsWith("강의실_"));
    }

    private void parseTimetablePdf(File file) throws IOException {
        try (PDDocument pdf = PDDocument.load(file)) {

            ObjectExtractor extractor = new ObjectExtractor(pdf);
            SpreadsheetExtractionAlgorithm sea
                    = new SpreadsheetExtractionAlgorithm();

            String filename = file.getName();

            String owner = filename.replace(".pdf", "")
                    .replace("교수님", "")
                    .replace("강의실_", "");

            Timetable timetable = new Timetable(owner);

            for (int pageNum = 1; pageNum <= pdf.getNumberOfPages(); pageNum++) {
                Page page = extractor.extract(pageNum);

                List<Table> tables = sea.extract(page);

                for (Table table : tables) {
                    List<List<RectangularTextContainer>> rows = table.getRows();

                    for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {

                        // 1~9교시만 사용
                        if (rowIndex > 9) {
                            break;
                        }

                        List<RectangularTextContainer> row = rows.get(rowIndex);

                        for (int colIndex = 1; colIndex <= 5; colIndex++) {
                            if (colIndex >= row.size()) {
                                continue;
                            }

                            String text = row.get(colIndex).getText();

                            if (text != null) {
                                text = text.trim();

                                if (!text.isEmpty()) {
                                    timetable.setCourse(
                                            rowIndex - 1,
                                            colIndex - 1,
                                             cleanTimetableText(text)
                                    );
                                }
                            }
                        }
                    }
                }
            }

            if (filename.contains("교수님")) {
                timetableStore.addProfessorSchedule(owner, timetable);
            } else {
                timetableStore.addClassroomSchedule(owner, timetable);
            }
        }
    }

    public RawDocument loadUrl(String url) throws IOException {
        org.jsoup.nodes.Document jsoupDoc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

        jsoupDoc.select(
                "script, style, nav, footer, header, aside, noscript"
        ).remove();

        org.jsoup.nodes.Element content
                = jsoupDoc.selectFirst(".sub_cont, #contents, .contents");

        String text = content != null
                ? content.text()
                : jsoupDoc.body().text();

        return new RawDocument(url, text);
    }

    private String loadTxt(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private String loadPdf(File file) throws IOException {
        try (PDDocument pdf = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdf);
        }
    }

    private String loadXlsx(File file) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(fis)) {

            DataFormatter formatter = new DataFormatter();

            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);

                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String val = formatter.formatCellValue(cell);

                        if (!val.isBlank()) {
                            sb.append(val).append(" ");
                        }
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    public record RawDocument(String source, String text) {

    }

    private String cleanTimetableText(String text) {
        return text
                .replaceAll("\\(인원:.*?\\)", "")
                .replaceAll("융합소", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
