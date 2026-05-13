/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.parser;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
/**
 *
 * @author wonsik
 */
public class LocalPdfParser implements DocumentParser{
    
    @Override
    public Document parse(InputStream inputStream) {
        try (PDDocument pdfDocument = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 텍스트 위치 기반 정렬 (줄글 문맥 유지에 좋음)
            stripper.setSortByPosition(true);
            
            String text = stripper.getText(pdfDocument);
            
            // LangChain4j 내부 Utils 대신 순수 자바로 Blank 체크
            if (text == null || text.trim().isEmpty()) {
                throw new BlankDocumentException();
            }
            return Document.from(text);
        } catch (IOException e) {
            throw new RuntimeException("PDF 파싱 오류", e);
        }
    }
}
