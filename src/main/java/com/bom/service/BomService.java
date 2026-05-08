package com.bom.service;

import com.bom.dao.BomItemDao;
import com.bom.dao.ComponentDao;
import com.bom.model.BomItem;
import com.bom.model.Component;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

public class BomService {
    private final ComponentDao componentDao = new ComponentDao();
    private final BomItemDao bomItemDao = new BomItemDao();

    /**
     * 对多个成品进行BOM汇总，展开到零件级别
     * 返回: List<汇总行>  每行 = 零件信息 + 总用量
     */
    public List<BomSummaryRow> summarize(List<Long> productIds) throws SQLException {
        List<BomRequest> requests = new ArrayList<>();
        for (Long productId : productIds) {
            requests.add(new BomRequest(productId, 1.0));
        }
        return summarizeWithQuantities(requests);
    }

    public List<BomSummaryRow> summarizeWithQuantities(List<BomRequest> requests) throws SQLException {
        // 一次性预载，递归只在内存里走，无 DB 往返
        Map<Long, Component> compCache = new HashMap<>();
        for (Component c : componentDao.findAll()) compCache.put(c.getId(), c);
        Map<Long, List<BomItem>> childCache = bomItemDao.findAllGrouped();

        Map<Long, PartSummaryAccumulator> partSummaries = new LinkedHashMap<>();
        for (BomRequest request : requests) {
            if (request == null || request.componentId == null || request.quantity <= 0) continue;
            expandSummary(request.componentId, request.quantity, partSummaries, new HashSet<>(),
                compCache, childCache, new ArrayList<>());
        }

        List<BomSummaryRow> result = new ArrayList<>();
        for (Map.Entry<Long, PartSummaryAccumulator> entry : partSummaries.entrySet()) {
            Component part = compCache.get(entry.getKey());
            if (part != null) {
                double totalQty = entry.getValue().totalQty;
                double stockQty = Math.max(0, part.getStockQty());
                double deductedQty = Math.min(totalQty, stockQty);
                double shortageQty = Math.max(0, totalQty - stockQty);
                result.add(new BomSummaryRow(
                    String.join("；", entry.getValue().paths),
                    part.getCode(), part.getName(), part.getSpec(),
                    part.getMaterial(), part.getUnit(), totalQty,
                    stockQty, deductedQty, shortageQty,
                    stockRemark(totalQty, stockQty, deductedQty, shortageQty)
                ));
            }
        }
        result.sort(Comparator.comparing(r -> r.code, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /**
     * 递归展开 BOM，到达零件层累加数量。
     * visited 用于检测当前递归路径上的循环引用——必须在所有 return 路径上回滚，
     * 否则同一零件在多个兄弟分支中复用时会被误判为循环。
     */
    private void expand(Long componentId, double multiplier,
                        Map<Long, Double> partQuantities,
                        Set<Long> visited,
                        Map<Long, Component> compCache,
                        Map<Long, List<BomItem>> childCache) throws SQLException {
        if (!visited.add(componentId)) {
            throw new SQLException("检测到BOM循环引用，组件ID: " + componentId);
        }
        try {
            Component comp = compCache.get(componentId);
            if (comp == null) return;

            if (isLeafType(comp.getType())) {
                partQuantities.merge(componentId, multiplier, Double::sum);
                return;
            }

            List<BomItem> children = childCache.getOrDefault(componentId, java.util.Collections.emptyList());
            for (BomItem child : children) {
                expand(child.getChildId(), multiplier * child.getQuantity(), partQuantities, visited, compCache, childCache);
            }
        } finally {
            visited.remove(componentId);
        }
    }

    private void expandSummary(Long componentId, double multiplier,
                               Map<Long, PartSummaryAccumulator> partSummaries,
                               Set<Long> visited,
                               Map<Long, Component> compCache,
                               Map<Long, List<BomItem>> childCache,
                               List<String> path) throws SQLException {
        if (!visited.add(componentId)) {
            throw new SQLException("检测到BOM循环引用，组件ID: " + componentId);
        }
        try {
            Component comp = compCache.get(componentId);
            if (comp == null) return;

            List<String> currentPath = new ArrayList<>(path);
            currentPath.add(pathLabel(comp));

            if (isLeafType(comp.getType())) {
                PartSummaryAccumulator acc = partSummaries.computeIfAbsent(componentId, k -> new PartSummaryAccumulator());
                acc.totalQty += multiplier;
                acc.paths.add(String.join(" > ", currentPath));
                return;
            }

            List<BomItem> children = childCache.getOrDefault(componentId, java.util.Collections.emptyList());
            for (BomItem child : children) {
                expandSummary(child.getChildId(), multiplier * child.getQuantity(), partSummaries,
                    visited, compCache, childCache, currentPath);
            }
        } finally {
            visited.remove(componentId);
        }
    }

    private String pathLabel(Component component) {
        String code = component.getCode() == null ? "" : component.getCode();
        String name = component.getName() == null ? "" : component.getName();
        return typeLabel(component.getType()) + " " + code + " - " + name;
    }

    private String typeLabel(String type) {
        if (Component.TYPE_PRODUCT.equals(type)) return "成品";
        if (Component.TYPE_SEMI.equals(type)) return "半成品";
        if (Component.TYPE_PURCHASE.equals(type)) return "外购件";
        if (Component.TYPE_PART.equals(type)) return "零件";
        return type == null ? "" : type;
    }

    private boolean isLeafType(String type) {
        return Component.TYPE_PART.equals(type) || Component.TYPE_PURCHASE.equals(type);
    }

    private String stockRemark(double totalQty, double stockQty, double deductedQty, double shortageQty) {
        if (stockQty <= 0) {
            return "无库存，需补 " + formatQty(totalQty);
        }
        if (shortageQty <= 0) {
            return "库存可覆盖，扣库存 " + formatQty(deductedQty) + "，剩余库存 " + formatQty(stockQty - totalQty);
        }
        return "扣库存 " + formatQty(deductedQty) + "，需补 " + formatQty(shortageQty);
    }

    /**
     * 导出BOM汇总到CSV文件
     */
    public void exportCsv(List<BomSummaryRow> rows, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "GBK"))) {
            writer.write("物料编号,物料名称,规格型号,材质,单位,总需求,库存数量,扣库存,需补数量,库存备注");
            writer.newLine();
            for (BomSummaryRow row : rows) {
                writer.write(escapeCsv(row.code) + "," + escapeCsv(row.name) + "," +
                    escapeCsv(row.spec) + "," + escapeCsv(row.material) + "," +
                    escapeCsv(row.unit) + "," + formatQty(row.totalQty) + "," +
                    formatQty(row.stockQty) + "," + formatQty(row.deductedQty) + "," +
                    formatQty(row.shortageQty) + "," + escapeCsv(row.stockRemark));
                writer.newLine();
            }
        }
    }

    /**
     * 导出BOM汇总到Excel文件
     */
    public void exportExcel(List<BomSummaryRow> rows, File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("BOM汇总");
            configurePrint(sheet);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(headerStyle);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setWrapText(true);
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(textStyle);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.###"));
            numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(numberStyle);

            // 标题行
            String[] headers = {"物料编号", "物料名称", "规格型号", "材质", "单位", "总需求", "库存数量", "扣库存", "需补数量", "库存备注"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            int rowNum = 1;
            for (BomSummaryRow row : rows) {
                Row r = sheet.createRow(rowNum++);
                createTextCell(r, 0, row.code, textStyle);
                createTextCell(r, 1, row.name, textStyle);
                createTextCell(r, 2, row.spec, textStyle);
                createTextCell(r, 3, row.material, textStyle);
                createTextCell(r, 4, row.unit, textStyle);
                createNumberCell(r, 5, row.totalQty, numberStyle);
                createNumberCell(r, 6, row.stockQty, numberStyle);
                createNumberCell(r, 7, row.deductedQty, numberStyle);
                createNumberCell(r, 8, row.shortageQty, numberStyle);
                createTextCell(r, 9, row.stockRemark, textStyle);
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNum - 1), 0, headers.length - 1));
            int[] widths = {14, 22, 24, 16, 10, 12, 12, 12, 12, 34};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    /**
     * 按成品导出BOM明细（每个成品展开到零件级别）
     */
    public List<BomExportRow> exportProductBom(List<Long> productIds) throws SQLException {
        Map<Long, Component> compCache = new HashMap<>();
        for (Component c : componentDao.findAll()) compCache.put(c.getId(), c);
        Map<Long, List<BomItem>> childCache = bomItemDao.findAllGrouped();

        List<BomExportRow> result = new ArrayList<>();
        for (Long productId : productIds) {
            Component product = compCache.get(productId);
            if (product == null) continue;
            Map<Long, Double> partQuantities = new LinkedHashMap<>();
            expand(productId, 1.0, partQuantities, new HashSet<>(), compCache, childCache);
            for (Map.Entry<Long, Double> entry : partQuantities.entrySet()) {
                Component part = compCache.get(entry.getKey());
                if (part != null) {
                    result.add(new BomExportRow(
                        product.getCode(), product.getName(),
                        part.getCode(), part.getName(), part.getSpec(),
                        part.getMaterial(), part.getUnit(), entry.getValue()
                    ));
                }
            }
        }
        return result;
    }

    /**
     * 导出成品BOM明细到CSV
     */
    public void exportProductBomCsv(List<Long> productIds, File file) throws SQLException, IOException {
        List<BomExportRow> rows = exportProductBom(productIds);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "GBK"))) {
            writer.write("成品编号,成品名称,物料编号,物料名称,规格型号,材质,单位,用量");
            writer.newLine();
            for (BomExportRow row : rows) {
                writer.write(escapeCsv(row.productCode) + "," + escapeCsv(row.productName) + "," +
                    escapeCsv(row.partCode) + "," + escapeCsv(row.partName) + "," +
                    escapeCsv(row.partSpec) + "," + escapeCsv(row.material) + "," +
                    escapeCsv(row.unit) + "," + formatQty(row.quantity));
                writer.newLine();
            }
        }
    }

    /**
     * 导出成品BOM明细到Excel（每个成品一个Sheet）
     */
    public void exportProductBomExcel(List<Long> productIds, File file) throws SQLException, IOException {
        Map<Long, Component> compCache = new HashMap<>();
        for (Component c : componentDao.findAll()) compCache.put(c.getId(), c);
        Map<Long, List<BomItem>> childCache = bomItemDao.findAllGrouped();

        Map<Long, Map<Long, Double>> productPartsMap = new LinkedHashMap<>();
        Map<Long, Component> productMap = new LinkedHashMap<>();
        for (Long productId : productIds) {
            Component product = compCache.get(productId);
            if (product == null) continue;
            productMap.put(productId, product);
            Map<Long, Double> partQuantities = new LinkedHashMap<>();
            expand(productId, 1.0, partQuantities, new HashSet<>(), compCache, childCache);
            productPartsMap.put(productId, partQuantities);
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"物料编号", "物料名称", "规格型号", "材质", "单位", "用量"};

            for (Long productId : productIds) {
                Component product = productMap.get(productId);
                Map<Long, Double> partQuantities = productPartsMap.get(productId);
                if (product == null || partQuantities == null) continue;

                String sheetName = product.getCode() != null ? product.getCode() : String.valueOf(productId);
                if (sheetName.length() > 31) sheetName = sheetName.substring(0, 31);
                Sheet sheet = workbook.createSheet(sheetName);
                configurePrint(sheet);

                // 成品信息行
                Row infoRow = sheet.createRow(0);
                infoRow.createCell(0).setCellValue("成品: " + product.getCode() + " - " + product.getName());

                Row headerRow = sheet.createRow(1);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int rowNum = 2;
                for (Map.Entry<Long, Double> entry : partQuantities.entrySet()) {
                    Component part = compCache.get(entry.getKey());
                    if (part == null) continue;
                    Row r = sheet.createRow(rowNum++);
                    r.createCell(0).setCellValue(part.getCode() != null ? part.getCode() : "");
                    r.createCell(1).setCellValue(part.getName() != null ? part.getName() : "");
                    r.createCell(2).setCellValue(part.getSpec() != null ? part.getSpec() : "");
                    r.createCell(3).setCellValue(part.getMaterial() != null ? part.getMaterial() : "");
                    r.createCell(4).setCellValue(part.getUnit() != null ? part.getUnit() : "");
                    r.createCell(5).setCellValue(entry.getValue());
                }

                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatQty(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) {
            return String.valueOf((long) qty);
        }
        return String.valueOf(qty);
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

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
    }

    private void createTextCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int index, double value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static class PartSummaryAccumulator {
        private double totalQty;
        private final LinkedHashSet<String> paths = new LinkedHashSet<>();
    }

    public static class BomRequest {
        public final Long componentId;
        public final double quantity;

        public BomRequest(Long componentId, double quantity) {
            this.componentId = componentId;
            this.quantity = quantity;
        }
    }

    /**
     * BOM汇总结果行
     */
    public static class BomSummaryRow {
        public final String hierarchyPath;
        public final String code;
        public final String name;
        public final String spec;
        public final String material;
        public final String unit;
        public final double totalQty;
        public final double stockQty;
        public final double deductedQty;
        public final double shortageQty;
        public final String stockRemark;

        public BomSummaryRow(String hierarchyPath, String code, String name, String spec, String material, String unit,
                             double totalQty, double stockQty, double deductedQty, double shortageQty, String stockRemark) {
            this.hierarchyPath = hierarchyPath;
            this.code = code;
            this.name = name;
            this.spec = spec;
            this.material = material;
            this.unit = unit;
            this.totalQty = totalQty;
            this.stockQty = stockQty;
            this.deductedQty = deductedQty;
            this.shortageQty = shortageQty;
            this.stockRemark = stockRemark;
        }
    }

    /**
     * 成品BOM导出行
     */
    public static class BomExportRow {
        public final String productCode;
        public final String productName;
        public final String partCode;
        public final String partName;
        public final String partSpec;
        public final String material;
        public final String unit;
        public final double quantity;

        public BomExportRow(String productCode, String productName,
                           String partCode, String partName, String partSpec,
                           String material, String unit, double quantity) {
            this.productCode = productCode;
            this.productName = productName;
            this.partCode = partCode;
            this.partName = partName;
            this.partSpec = partSpec;
            this.material = material;
            this.unit = unit;
            this.quantity = quantity;
        }
    }
}
