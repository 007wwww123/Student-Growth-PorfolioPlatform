package com.college.growth.export.service;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 单人档案 PDF 渲染（F-12 / AC-041–043）。
 *
 * <p>使用 PDFBox 直接绘制：标题、六板块字段、已认定证书/竞赛、五项水印
 * （姓名、学号、导出人、时间、「仅供内部使用」）。中文字体通过
 * {@code growth.export.font-path} 配置，默认 Windows 黑体；缺失时明确失败，
 * 不退回 Helvetica（会得到方框或乱码）。</p>
 */
@Component
public class ProfilePdfRenderer {

    private static final float MARGIN = 50F;

    private static final float WIDTH = PDRectangle.A4.getWidth();

    private static final float HEIGHT = PDRectangle.A4.getHeight();

    private static final float BODY_SIZE = 10F;

    private static final float TITLE_SIZE = 18F;

    private static final float SECTION_SIZE = 13F;

    private final String fontPath;

    public ProfilePdfRenderer(@Value("${growth.export.font-path:C:/Windows/Fonts/simhei.ttf}") String fontPath) {
        this.fontPath = fontPath;
    }

    public byte[] render(ProfileExportData data) {
        File fontFile = new File(fontPath);
        if (!fontFile.isFile()) {
            throw new BusinessException(ResultCode.PDF_RENDER_TIMEOUT,
                    "PDF 中文字体不存在：" + fontPath + "，请配置 growth.export.font-path");
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontFile);
            PDPage page = newPage(document, data);
            PDPageContentStream content = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true);
            float y = HEIGHT - MARGIN;
            y = title(document, page, content, font, data, y);
            for (ProfileExportData.Section section : data.getSections()) {
                if (y < MARGIN + 80) {
                    content.close();
                    page = newPage(document, data);
                    content = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true);
                    y = HEIGHT - MARGIN;
                }
                y = section(content, font, section, y);
            }
            if (!data.getCertificates().isEmpty() || !data.getEvents().isEmpty()) {
                if (y < MARGIN + 80) {
                    content.close();
                    page = newPage(document, data);
                    content = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true);
                    y = HEIGHT - MARGIN;
                }
                y = achievements(content, font, data, y);
            }
            content.close();
            addPageNumbers(document, font);
            document.save(out);
            return out.toByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.PDF_RENDER_TIMEOUT, "PDF 生成失败：" + ex.getMessage());
        }
    }

    private PDPage newPage(PDDocument document, ProfileExportData data) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        watermark(document, page, data);
        return page;
    }

    private void watermark(PDDocument document, PDPage page, ProfileExportData data) throws IOException {
        String text = data.getStudentName() + "  " + data.getStudentNo() + "  "
                + data.getExporterName() + "  " + data.getExportedAt() + "  仅供内部使用";
        try (PDPageContentStream stream = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            PDType0Font font = PDType0Font.load(document, new File(fontPath));
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.setNonStrokingAlphaConstant(0.08F);
            stream.setGraphicsStateParameters(state);
            stream.setNonStrokingColor(Color.GRAY);
            stream.setFont(font, 11);
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 3; col++) {
                    float x = 40 + col * 190;
                    float y = 80 + row * 110;
                    stream.beginText();
                    stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), x, y));
                    stream.showText(text);
                    stream.endText();
                }
            }
        }
    }

    private float title(PDDocument document, PDPage page, PDPageContentStream content,
                        PDType0Font font, ProfileExportData data, float y) throws IOException {
        content.beginText();
        content.setFont(font, TITLE_SIZE);
        content.newLineAtOffset(MARGIN, y);
        content.showText("学生成长档案");
        content.endText();
        y -= 30;
        y = line(content, font, BODY_SIZE, "姓名：" + data.getStudentName()
                + "    学号：" + data.getStudentNo(), y);
        y = line(content, font, BODY_SIZE, "班级：" + nullSafe(data.getClassName())
                + "    专业：" + nullSafe(data.getMajorName())
                + "    学年：" + data.getAcademicYear(), y);
        y = line(content, font, BODY_SIZE, "导出人：" + data.getExporterName()
                + "    导出时间：" + data.getExportedAt(), y);
        y -= 8;
        return y;
    }

    private float section(PDPageContentStream content, PDType0Font font,
                          ProfileExportData.Section section, float y) throws IOException {
        content.beginText();
        content.setFont(font, SECTION_SIZE);
        content.newLineAtOffset(MARGIN, y);
        content.showText(section.getName() + "（" + section.getStatus() + "，完成度 "
                + section.getCompleteness() + "%）");
        content.endText();
        y -= 20;
        if (section.getLines().isEmpty()) {
            y = line(content, font, BODY_SIZE, "（暂无内容）", y);
        }
        for (ProfileExportData.Line item : section.getLines()) {
            y = line(content, font, BODY_SIZE, item.getLabel() + "：" + nullSafe(item.getValue()), y);
        }
        y -= 6;
        return y;
    }

    private float achievements(PDPageContentStream content, PDType0Font font,
                               ProfileExportData data, float y) throws IOException {
        content.beginText();
        content.setFont(font, SECTION_SIZE);
        content.newLineAtOffset(MARGIN, y);
        content.showText("已认定成果");
        content.endText();
        y -= 20;
        for (String certificate : data.getCertificates()) {
            y = line(content, font, BODY_SIZE, "证书：" + certificate, y);
        }
        for (String event : data.getEvents()) {
            y = line(content, font, BODY_SIZE, "竞赛/科研：" + event, y);
        }
        return y;
    }

    private float line(PDPageContentStream content, PDType0Font font, float size,
                       String text, float y) throws IOException {
        for (String part : wrap(text, font, size, WIDTH - MARGIN * 2)) {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(MARGIN, y);
            content.showText(part);
            content.endText();
            y -= size + 5;
        }
        return y;
    }

    private List<String> wrap(String text, PDType0Font font, float size, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        float width = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                width = 0;
                continue;
            }
            float charWidth = font.getStringWidth(String.valueOf(ch)) / 1000 * size;
            if (width + charWidth > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                width = 0;
            }
            current.append(ch);
            width += charWidth;
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void addPageNumbers(PDDocument document, PDType0Font font) throws IOException {
        int total = document.getNumberOfPages();
        for (int index = 0; index < total; index++) {
            PDPage page = document.getPage(index);
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setFont(font, 9);
                stream.newLineAtOffset(WIDTH - MARGIN - 60, 30);
                stream.showText("第 " + (index + 1) + " / " + total + " 页");
                stream.endText();
            }
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
