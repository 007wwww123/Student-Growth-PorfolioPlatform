package com.college.growth.academic.record.service;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学业成果导入模板解析（F-10）。
 *
 * <p>只解析文件，业务校验在 Service；表头顺序固定，不接受按列名猜位置。</p>
 */
@Component
public class AcademicImportParser {

    public static final List<String> RANK_HEADERS = List.of(
            "学号", "学期", "班级排名", "专业排名", "GPA");

    public static final List<String> CERT_HEADERS = List.of(
            "学号", "证书类型", "证书名称", "成绩", "取得日期");

    public static final int MAX_ROWS = 2000;

    public static final long MAX_SIZE = 20L * 1024 * 1024;

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Data
    public static class ParseResult<T> {

        private List<String> headers;

        private List<T> rows;
    }

    @Data
    public static class RankRow {

        private int rowNo;

        private String studentNo;

        private String term;

        private String classRank;

        private String majorRank;

        private String gpa;
    }

    @Data
    public static class CertRow {

        private int rowNo;

        private String studentNo;

        private String certType;

        private String certName;

        private String score;

        private String obtainDate;
    }

    public ParseResult<RankRow> parseRanks(MultipartFile file) {
        Sheet sheet = sheetOf(file, RANK_HEADERS);
        List<RankRow> rows = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (isBlank(row, RANK_HEADERS.size())) {
                continue;
            }
            RankRow target = new RankRow();
            target.setRowNo(index + 1);
            target.setStudentNo(cell(row.getCell(0)));
            target.setTerm(cell(row.getCell(1)));
            target.setClassRank(cell(row.getCell(2)));
            target.setMajorRank(cell(row.getCell(3)));
            target.setGpa(cell(row.getCell(4)));
            rows.add(target);
            checkLimit(rows.size());
        }
        return result(RANK_HEADERS, rows);
    }

    public ParseResult<CertRow> parseCertificates(MultipartFile file) {
        Sheet sheet = sheetOf(file, CERT_HEADERS);
        List<CertRow> rows = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (isBlank(row, CERT_HEADERS.size())) {
                continue;
            }
            CertRow target = new CertRow();
            target.setRowNo(index + 1);
            target.setStudentNo(cell(row.getCell(0)));
            target.setCertType(cell(row.getCell(1)));
            target.setCertName(cell(row.getCell(2)));
            target.setScore(cell(row.getCell(3)));
            target.setObtainDate(cell(row.getCell(4)));
            rows.add(target);
            checkLimit(rows.size());
        }
        return result(CERT_HEADERS, rows);
    }

    private Sheet sheetOf(MultipartFile file, List<String> expected) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "请选择要导入的 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "仅支持 .xlsx 文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "文件不能超过 20MB");
        }
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headers = headers(sheet.getRow(0));
            if (!expected.equals(headers)) {
                throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID,
                        "表头列名或顺序不正确，应为：" + String.join(" | ", expected)
                                + "；实际：" + String.join(" | ", headers));
            }
            return sheet;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID,
                    "文件解析失败：" + ex.getMessage());
        }
    }

    private <T> ParseResult<T> result(List<String> headers, List<T> rows) {
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "文件中没有数据行");
        }
        ParseResult<T> result = new ParseResult<>();
        result.setHeaders(headers);
        result.setRows(rows);
        return result;
    }

    private void checkLimit(int size) {
        if (size > MAX_ROWS) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID,
                    "单次最多导入 " + MAX_ROWS + " 行，请分批上传");
        }
    }

    private List<String> headers(Row row) {
        List<String> headers = new ArrayList<>();
        if (row == null) {
            return headers;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            headers.add(cell(row.getCell(i)));
        }
        while (!headers.isEmpty() && headers.get(headers.size() - 1).isEmpty()) {
            headers.remove(headers.size() - 1);
        }
        return headers;
    }

    private boolean isBlank(Row row, int columns) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < columns; i++) {
            if (!cell(row.getCell(i)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cell(Cell cell) {
        if (cell == null) {
            return "";
        }
        String value = FORMATTER.formatCellValue(cell);
        return value == null ? "" : value.trim();
    }
}
