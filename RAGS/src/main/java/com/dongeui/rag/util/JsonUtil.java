package com.dongeui.rag.util;

import com.dongeui.rag.model.TimetableEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

public class JsonUtil {

    private static final ObjectMapper mapper =
            new ObjectMapper();

    /**
     * JSON 저장
     */
    public static void save(
            List<TimetableEntry> entries,
            File file
    ) {

        try {

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, entries);

        } catch (Exception e) {

            System.out.println(
                    "JSON 저장 실패: "
                            + e.getMessage()
            );
        }
    }

    /**
     * JSON 로드
     */
    public static List<TimetableEntry> load(
            File file
    ) {

        try {

            return mapper.readValue(
                    file,
                    new TypeReference<List<TimetableEntry>>() {}
            );

        } catch (Exception e) {

            System.out.println(
                    "JSON 로드 실패: "
                            + e.getMessage()
            );

            return List.of();
        }
    }
}