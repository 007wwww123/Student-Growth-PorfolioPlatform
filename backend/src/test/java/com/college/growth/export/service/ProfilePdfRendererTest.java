package com.college.growth.export.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PDF 渲染冒烟：中文字体、水印与成果段落。 */
class ProfilePdfRendererTest {

    private static final String FONT = "C:/Windows/Fonts/simhei.ttf";

    @Test
    @DisplayName("render chinese profile with watermark")
    void renderChineseProfile() throws Exception {
        Assumptions.assumeTrue(new File(FONT).isFile(), "missing test font " + FONT);

        ProfileExportData data = new ProfileExportData();
        data.setStudentName("李明");
        data.setStudentNo("2024010101");
        data.setClassName("软件2401");
        data.setMajorName("软件工程");
        data.setAcademicYear("2025-2026-1");
        data.setExporterName("辅导员甲");
        data.setExportedAt("2026-11-28 18:00:00");

        ProfileExportData.Section section = new ProfileExportData.Section();
        section.setName("基本信息");
        section.setStatus("已通过");
        section.setCompleteness("100");
        section.setLines(List.of(new ProfileExportData.Line("姓名", "李明")));
        data.setSections(List.of(section));
        data.setCertificates(List.of("大学英语四级（CET-4）"));
        data.setEvents(List.of("数学建模竞赛（NATIONAL）"));

        ProfilePdfRenderer renderer = new ProfilePdfRenderer(FONT);
        byte[] bytes = renderer.render(data);

        assertTrue(bytes.length > 1000);
        assertTrue(new String(bytes, 0, 5).startsWith("%PDF"));
        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            String compact = text.replaceAll("\\s+", "");
            assertTrue(compact.contains("李明"), "missing student name");
            assertTrue(compact.contains("仅供"), "missing watermark");
            assertTrue(compact.contains("已认定成果"), "missing achievements section");
        }
    }
}
