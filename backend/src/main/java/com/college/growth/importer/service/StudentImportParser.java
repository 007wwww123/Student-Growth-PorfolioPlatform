package com.college.growth.importer.service;

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
 * 学生导入模板解析（F-09 / S-2）。
 *
 * <p>只负责「文件 → 行 + 原始字段」，业务校验与入库在 Service；
 * 表头列名与顺序必须与模板一致，不接受「按列名猜位置」。</p>
 */
@Component
public class StudentImportParser {

    /** 模板列顺序（S-2-01）。 */
    public static final List<String> HEADERS = List.of(
            "学号", "姓名", "性别", "民族", "身份证号", "籍贯", "班级编码", "入学年份");

    public static final int MAX_ROWS = 2000;

    public static final long MAX_SIZE = 20L * 1024 * 1024;

    private static final DataFormatter FORMATTER = new DataFormatter();

    /** 解析结果。 */
    @Data
    public static class ParseResult {

        private List<String> headers;

        private List<StudentRow> rows;
    }

    /** 一行学生数据。 */
    @Data
    public static class StudentRow {

        private int rowNo;

        private String studentNo;

        private String name;

        private String gender;

        private String nation;

        private String idCard;

        private String nativePlace;

        private String classCode;

        private String enrollmentYear;
    }

    public ParseResult parse(MultipartFile file) {
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
            if (sheet == null) {
                throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "文件没有工作表");
            }
            List<String> headers = readHeaders(sheet.getRow(0));
            if (!HEADERS.equals(headers)) {
                throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID,
                        "表头列名或顺序不正确，应为：" + String.join(" | ", HEADERS)
                                + "；实际：" + String.join(" | ", headers));
            }
            List<StudentRow> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (isBlank(row)) {
                    continue;
                }
                rows.add(readRow(row));
                if (rows.size() > MAX_ROWS) {
                    throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID,
                            "单次最多导入 " + MAX_ROWS + " 行，请分批上传");
                }
            }
            if (rows.isEmpty()) {
                throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID, "文件中没有数据行");
            }
            ParseResult result = new ParseResult();
            result.setHeaders(headers);
            result.setRows(rows);
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.IMPORT_TEMPLATE_INVALID,
                    "文件解析失败，请确认使用模板：" + ex.getMessage());
        }
    }

    private List<String> readHeaders(Row row) {
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

    private StudentRow readRow(Row row) {
        StudentRow target = new StudentRow();
        target.setRowNo(row.getRowNum() + 1);
        target.setStudentNo(cell(row.getCell(0)));
        target.setName(cell(row.getCell(1)));
        target.setGender(cell(row.getCell(2)));
        target.setNation(cell(row.getCell(3)));
        target.setIdCard(cell(row.getCell(4)));
        target.setNativePlace(cell(row.getCell(5)));
        target.setClassCode(cell(row.getCell(6)));
        target.setEnrollmentYear(cell(row.getCell(7)));
        return target;
    }

    private boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < HEADERS.size(); i++) {
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
