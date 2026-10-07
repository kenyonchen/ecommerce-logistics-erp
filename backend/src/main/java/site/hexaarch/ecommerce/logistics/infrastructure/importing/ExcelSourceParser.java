package site.hexaarch.ecommerce.logistics.infrastructure.importing;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel(.xlsx/.xls) 解析支撑：将工作簿首个Sheet转换为CSV文本，
 * 之后的字段映射、预览、导入流程与CSV完全复用，保证三类导入（订单/商品/客户）行为一致。
 *
 * @author kenyon
 */
@Slf4j
@Component
public class ExcelSourceParser {

    /**
     * 判断上传文件是否为Excel
     */
    public boolean isExcel(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    /**
     * 将Excel首个Sheet转换为CSV（首行为表头）
     */
    public String toCsv(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ValidationException("Excel文件中没有可解析的工作表");
            }

            DataFormatter formatter = new DataFormatter();
            List<String> lines = new ArrayList<>();
            for (Row row : sheet) {
                int lastColumn = resolveLastColumn(row);
                if (lastColumn < 0) {
                    continue;
                }
                List<String> fields = new ArrayList<>(lastColumn + 1);
                for (int c = 0; c <= lastColumn; c++) {
                    fields.add(escape(row.getCell(c) == null ? "" : formatter.formatCellValue(row.getCell(c))));
                }
                lines.add(String.join(",", fields));
            }

            if (lines.isEmpty()) {
                throw new ValidationException("Excel文件内容为空");
            }
            return String.join("\n", lines);
        } catch (ValidationException e) {
            throw e;
        } catch (IOException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            log.warn("[ImportFile] Excel解析失败: {}", e.getMessage());
            throw new ValidationException("Excel文件解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[ImportFile] Excel解析失败: {}", e.getMessage());
            throw new ValidationException("Excel文件解析失败，请确认是合法的xlsx/xls文件: " + e.getMessage());
        }
    }

    /**
     * 计算行的最后有效列（忽略行尾空单元格，跳过全空行）
     */
    private int resolveLastColumn(Row row) {
        int last = -1;
        for (int c = row.getFirstCellNum() < 0 ? 0 : row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = new DataFormatter().formatCellValue(cell);
                if (value != null && !value.isBlank()) {
                    last = c;
                }
            }
        }
        return last;
    }

    /**
     * CSV转义：包含逗号、引号或换行时用双引号包裹
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
