/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 *
 * @author wonsik
 */
public class MarkdownCsvParser implements DocumentParser{
    @Override
    public Document parse(InputStream inputStream) {
        StringBuilder mdBuilder = new StringBuilder();

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT)) {
             
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) return new Document("");

            // 헤더 조립
            CSVRecord header = records.get(0);
            mdBuilder.append("| ");
            for (String h : header) mdBuilder.append(h.replace("\n", " ").trim()).append(" | ");
            mdBuilder.append("\n|");
            for (int i = 0; i < header.size(); i++) mdBuilder.append(" :--- |");
            mdBuilder.append("\n");

            // 데이터 조립
            for (int i = 1; i < records.size(); i++) {
                mdBuilder.append("| ");
                for (String cell : records.get(i)) {
                    mdBuilder.append(cell.replaceAll("\\n", " / ").trim()).append(" | "); // replaceAll("\\s+", " ")
                }
                mdBuilder.append("\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV 파싱 오류", e);
        }

        return Document.from(mdBuilder.toString());
    }
}
