package com.bom.service;

import com.bom.dao.ComponentDao;
import com.bom.model.Component;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ComponentSheetService {
    private static final String[] HEADERS = {
        "ID", "类型", "编号", "名称", "规格型号", "单位", "材质", "库存数量", "备注"
    };

    private final ComponentDao componentDao = new ComponentDao();

    public void exportAllComponents(File file) throws SQLException, IOException {
        List<Component> components = componentDao.findAll();
        components.sort(Comparator
            .comparingInt((Component c) -> typeOrder(c.getType()))
            .thenComparing(Component::getCode, Comparator.nullsLast(Comparator.naturalOrder())));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("物料清单");
            configurePrint(sheet);

            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);
            CellStyle numberStyle = numberStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(24);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BOM 物料清单");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            Row hintRow = sheet.createRow(1);
            hintRow.createCell(0).setCellValue("可修改后导入；类型可填：零件、外购件、半成品、成品。编号为空时会按类型自动生成。");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, HEADERS.length - 1));

            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 3;
            for (Component component : components) {
                Row row = sheet.createRow(rowIndex++);
                createNumberCell(row, 0, component.getId(), numberStyle);
                createTextCell(row, 1, typeLabel(component.getType()), textStyle);
                createTextCell(row, 2, component.getCode(), textStyle);
                createTextCell(row, 3, component.getName(), textStyle);
                createTextCell(row, 4, component.getSpec(), textStyle);
                createTextCell(row, 5, component.getUnit(), textStyle);
                createTextCell(row, 6, component.getMaterial(), textStyle);
                createNumberCell(row, 7, component.getStockQty(), numberStyle);
                createTextCell(row, 8, component.getRemark(), textStyle);
            }

            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, HEADERS.length - 1));
            int[] widths = {10, 12, 14, 22, 24, 10, 16, 12, 30};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
        }
    }

    public ImportResult importComponents(File file) throws IOException, SQLException {
        ImportResult result = new ImportResult();
        DataFormatter formatter = new DataFormatter();
        try (FileInputStream in = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowIndex = findHeaderRow(sheet, formatter);
            if (headerRowIndex < 0) {
                result.addError("未找到表头，请使用导出的物料清单模板。");
                return result;
            }

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isBlankRow(row, formatter)) continue;
                try {
                    Component component = readComponent(row, formatter);
                    boolean exists = exists(component);
                    componentDao.saveFromImport(component);
                    if (exists) result.updated++; else result.inserted++;
                } catch (Exception e) {
                    result.addError("第 " + (i + 1) + " 行: " + e.getMessage());
                }
            }
        }
        return result;
    }

    private Component readComponent(Row row, DataFormatter formatter) throws SQLException {
        Component c = new Component();
        c.setId(parseLong(cellText(row, 0, formatter)));
        String type = parseType(cellText(row, 1, formatter));
        c.setType(type);
        String code = cellText(row, 2, formatter).trim();
        if (code.isEmpty()) {
            code = componentDao.nextCode(type);
        }
        c.setCode(code);
        c.setName(required(cellText(row, 3, formatter), "名称不能为空"));
        c.setSpec(cellText(row, 4, formatter).trim());
        c.setUnit(cellText(row, 5, formatter).trim());
        c.setMaterial(cellText(row, 6, formatter).trim());
        c.setStockQty(parseNonNegativeDouble(cellText(row, 7, formatter)));
        c.setRemark(cellText(row, 8, formatter).trim());
        return c;
    }

    private boolean exists(Component c) throws SQLException {
        if (c.getId() != null && componentDao.findById(c.getId()) != null) {
            return true;
        }
        return componentDao.findByCode(c.getCode()) != null;
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if ("ID".equals(cellText(row, 0, formatter).trim())
                    && "类型".equals(cellText(row, 1, formatter).trim())
                    && "编号".equals(cellText(row, 2, formatter).trim())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (int i = 0; i < HEADERS.length; i++) {
            if (!cellText(row, i, formatter).trim().isEmpty()) return false;
        }
        return true;
    }

    private String cellText(Row row, int index, DataFormatter formatter) {
        if (row == null) return "";
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private Long parseLong(String text) {
        String value = text == null ? "" : text.trim().replace(",", "");
        if (value.isEmpty()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return (long) Double.parseDouble(value);
        }
    }

    private double parseNonNegativeDouble(String text) {
        String value = text == null ? "" : text.trim().replace(",", "");
        if (value.isEmpty()) return 0;
        double qty = Double.parseDouble(value);
        if (qty < 0) throw new NumberFormatException("库存数量不能小于 0");
        return qty;
    }

    private String required(String text, String message) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        return value;
    }

    public static String typeLabel(String type) {
        if (Component.TYPE_PART.equals(type)) return "零件";
        if (Component.TYPE_PURCHASE.equals(type)) return "外购件";
        if (Component.TYPE_SEMI.equals(type)) return "半成品";
        if (Component.TYPE_PRODUCT.equals(type)) return "成品";
        return type == null ? "" : type;
    }

    public static String parseType(String text) {
        String raw = text == null ? "" : text.trim();
        String value = raw.toUpperCase(Locale.ROOT);
        if (value.isEmpty() || "零件".equals(raw)) return Component.TYPE_PART;
        if ("PART".equals(value)) return Component.TYPE_PART;
        if ("外购件".equals(raw) || "外购".equals(raw) || "采购件".equals(raw) || "PURCHASE".equals(value)) {
            return Component.TYPE_PURCHASE;
        }
        if ("半成品".equals(raw) || "SEMI".equals(value)) return Component.TYPE_SEMI;
        if ("成品".equals(raw) || "PRODUCT".equals(value)) return Component.TYPE_PRODUCT;
        throw new IllegalArgumentException("未知类型: " + text);
    }

    private int typeOrder(String type) {
        if (Component.TYPE_PART.equals(type)) return 1;
        if (Component.TYPE_PURCHASE.equals(type)) return 2;
        if (Component.TYPE_SEMI.equals(type)) return 3;
        if (Component.TYPE_PRODUCT.equals(type)) return 4;
        return 9;
    }

    private void configurePrint(Sheet sheet) {
        sheet.setFitToPage(true);
        sheet.setMargin(Sheet.TopMargin, 0.35);
        sheet.setMargin(Sheet.BottomMargin, 0.35);
        sheet.setMargin(Sheet.LeftMargin, 0.25);
        sheet.setMargin(Sheet.RightMargin, 0.25);
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle numberStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.###"));
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle borderedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        return style;
    }

    private void createTextCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int index, Long value, CellStyle style) {
        Cell cell = row.createCell(index);
        if (value != null) cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int index, double value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static class ImportResult {
        public int inserted;
        public int updated;
        public final List<String> errors = new ArrayList<>();

        private void addError(String error) {
            errors.add(error);
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("导入完成：新增 ").append(inserted)
              .append(" 条，更新 ").append(updated).append(" 条");
            if (!errors.isEmpty()) {
                sb.append("，失败 ").append(errors.size()).append(" 行");
                int limit = Math.min(errors.size(), 8);
                for (int i = 0; i < limit; i++) {
                    sb.append("\n").append(errors.get(i));
                }
                if (errors.size() > limit) {
                    sb.append("\n其余错误请检查表格后重新导入。");
                }
            }
            return sb.toString();
        }
    }
}
