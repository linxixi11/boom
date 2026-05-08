package com.bom.ui;

import com.bom.dao.ComponentDao;
import com.bom.model.Component;
import com.bom.service.BomService;
import com.bom.service.BomService.BomRequest;
import com.bom.service.BomService.BomSummaryRow;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选/已选两栏 + 结果区。
 * 左上：候选成品/半成品（搜索 + 类型过滤）
 * 左下：已选清单
 * 右侧：BOM 汇总结果 + 导出
 */
public class BomPanel extends JPanel {
    private final ComponentDao componentDao = new ComponentDao();
    private final BomService bomService = new BomService();

    private final DefaultTableModel candidateModel;
    private final JTable candidateTable;
    private final JTextField searchField;
    private final JComboBox<String> typeFilter;

    private final DefaultTableModel pickedModel;
    private final JTable pickedTable;
    private final Map<Long, Double> pickedQuantities = new LinkedHashMap<>();
    private final JLabel pickedCount;

    private final DefaultTableModel resultModel;
    private final JTable resultTable;
    private final JLabel resultCount;

    private final JProgressBar progressBar;
    private final JButton generateBtn;
    private PageFormat resultPageFormat = TablePrintSupport.defaultLandscapeA4();
    private List<BomSummaryRow> currentRows = new ArrayList<>();
    private List<Component> allCandidates = new ArrayList<>();

    public BomPanel() {
        setLayout(new BorderLayout());
        setBackground(UIStyle.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // ====== 候选区 ======
        JPanel candPanel = UIStyle.section();
        JPanel candHeader = new JPanel(new BorderLayout(8, 0));
        candHeader.setOpaque(false);
        candHeader.add(UIStyle.sectionLabel("候选成品 / 半成品 / 零件"), BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterPanel.setOpaque(false);
        typeFilter = new JComboBox<>(new String[]{"全部", "成品", "半成品", "零件", "外购件"});
        typeFilter.setFont(UIStyle.FONT);
        searchField = new JTextField(14);
        searchField.setFont(UIStyle.FONT);
        searchField.setToolTipText("搜索编号 / 名称 / 规格");
        filterPanel.add(new JLabel("类型"));
        filterPanel.add(typeFilter);
        filterPanel.add(new JLabel("搜索"));
        filterPanel.add(searchField);
        candHeader.add(filterPanel, BorderLayout.EAST);
        candPanel.add(candHeader, BorderLayout.NORTH);

        candidateModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "规格", "单位"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        candidateTable = UIStyle.createTable(candidateModel);
        UIStyle.hideColumn(candidateTable, 0);
        candidateTable.getColumnModel().getColumn(1).setMaxWidth(70);
        candidateTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        candPanel.add(UIStyle.wrap(candidateTable), BorderLayout.CENTER);

        JButton addBtn = UIStyle.primaryButton("加入选择 ↓");
        JLabel candHint = UIStyle.hintLabel("双击候选行也可加入");
        JPanel candFooter = new JPanel(new BorderLayout());
        candFooter.setOpaque(false);
        candFooter.add(UIStyle.buttonRow(addBtn), BorderLayout.WEST);
        candFooter.add(candHint, BorderLayout.EAST);
        candPanel.add(candFooter, BorderLayout.SOUTH);

        // ====== 已选区 ======
        JPanel pickedPanel = UIStyle.section();
        pickedCount = UIStyle.hintLabel("已选 0 项");
        JPanel pickedHeader = new JPanel(new BorderLayout());
        pickedHeader.setOpaque(false);
        pickedHeader.add(UIStyle.sectionLabel("已选清单"), BorderLayout.WEST);
        pickedHeader.add(pickedCount, BorderLayout.EAST);
        pickedPanel.add(pickedHeader, BorderLayout.NORTH);

        pickedModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "单位", "数量"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 5; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        pickedTable = UIStyle.createTable(pickedModel);
        UIStyle.hideColumn(pickedTable, 0);
        pickedTable.getColumnModel().getColumn(1).setMaxWidth(70);
        pickedTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        pickedPanel.add(UIStyle.wrap(pickedTable), BorderLayout.CENTER);

        JButton removeBtn = UIStyle.button("移除选中");
        JButton clearBtn = UIStyle.button("清空");
        generateBtn = UIStyle.primaryButton("生成 BOM 汇总 →");
        pickedPanel.add(UIStyle.buttonRow(removeBtn, clearBtn, generateBtn), BorderLayout.SOUTH);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, candPanel, pickedPanel);
        leftSplit.setResizeWeight(0.6);
        leftSplit.setBorder(null);
        leftSplit.setDividerSize(8);
        leftSplit.setOpaque(false);

        // ====== 结果区 ======
        JPanel rightPanel = UIStyle.section();
        resultCount = UIStyle.hintLabel("共 0 行");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("汇总中…");
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(0, 20));
        progressBar.setVisible(false);
        JPanel resultHeader = new JPanel(new BorderLayout(6, 0));
        resultHeader.setOpaque(false);
        resultHeader.add(UIStyle.sectionLabel("BOM 汇总结果（库存）"), BorderLayout.WEST);
        JPanel eastPanel = new JPanel(new BorderLayout(8, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(progressBar, BorderLayout.CENTER);
        eastPanel.add(resultCount, BorderLayout.EAST);
        resultHeader.add(eastPanel, BorderLayout.EAST);
        rightPanel.add(resultHeader, BorderLayout.NORTH);

        resultModel = new DefaultTableModel(
            new String[]{"物料编号", "物料名称", "规格型号", "材质", "单位", "总需求", "库存数量", "扣库存", "需补数量", "库存备注"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultTable = UIStyle.createTable(resultModel);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        rightPanel.add(UIStyle.wrap(resultTable), BorderLayout.CENTER);

        JButton csvBtn = UIStyle.button("导出 CSV");
        JButton excelBtn = UIStyle.button("导出 Excel");
        JButton pageSetupBtn = UIStyle.button("页面设置");
        JButton previewBtn = UIStyle.button("打印预览");
        JButton printBtn = UIStyle.primaryButton("打印");
        rightPanel.add(UIStyle.buttonRow(csvBtn, excelBtn, pageSetupBtn, previewBtn, printBtn), BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightPanel);
        mainSplit.setDividerLocation(460);
        mainSplit.setResizeWeight(0.42);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        mainSplit.setOpaque(false);
        add(mainSplit, BorderLayout.CENTER);

        // ====== 事件 ======
        DocumentListener filterListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        };
        searchField.getDocument().addDocumentListener(filterListener);
        typeFilter.addActionListener(e -> applyFilter());

        addBtn.addActionListener(e -> addSelectedToPicked());
        candidateTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addSelectedToPicked();
            }
        });

        removeBtn.addActionListener(e -> removePicked());
        clearBtn.addActionListener(e -> {
            pickedModel.setRowCount(0);
            pickedQuantities.clear();
            updatePickedCount();
            clearResult();
        });
        generateBtn.addActionListener(e -> generateBom());
        csvBtn.addActionListener(e -> exportCsv());
        excelBtn.addActionListener(e -> exportExcel());
        pageSetupBtn.addActionListener(e -> resultPageFormat = TablePrintSupport.showPageSetup(this, resultPageFormat));
        previewBtn.addActionListener(e -> TablePrintSupport.showPreview(this, resultTable, resultPageFormat, "BOM 汇总清单"));
        printBtn.addActionListener(e -> TablePrintSupport.print(this, resultTable, resultPageFormat, "BOM 汇总清单"));

        refreshData();
    }

    public void refreshData() {
        try {
            allCandidates = new ArrayList<>();
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PRODUCT));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_SEMI));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PART));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PURCHASE));
            applyFilter();
            // 保留已选项中仍然存在的 ID
            Map<Long, Component> existing = new LinkedHashMap<>();
            for (Component c : allCandidates) existing.put(c.getId(), c);
            pickedQuantities.keySet().removeIf(id -> !existing.containsKey(id));
            // 重建 picked 表
            pickedModel.setRowCount(0);
            for (Long id : pickedQuantities.keySet()) {
                Component c = findInList(id);
                if (c != null) {
                    pickedModel.addRow(new Object[]{
                        c.getId(), typeLabel(c.getType()), c.getCode(), c.getName(), c.getUnit(),
                        formatQty(pickedQuantities.get(id))
                    });
                }
            }
            updatePickedCount();
            resultModel.setRowCount(0);
            currentRows = new ArrayList<>();
            updateResultCount();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载失败: " + e.getMessage());
        }
    }

    private Component findInList(Long id) {
        for (Component c : allCandidates) if (c.getId().equals(id)) return c;
        return null;
    }

    private static String typeLabel(String type) {
        return Component.TYPE_PRODUCT.equals(type) ? "成品"
             : Component.TYPE_SEMI.equals(type) ? "半成品"
             : Component.TYPE_PURCHASE.equals(type) ? "外购件"
             : Component.TYPE_PART.equals(type) ? "零件"
             : type;
    }

    private void applyFilter() {
        String kw = searchField.getText().trim().toLowerCase();
        String type = (String) typeFilter.getSelectedItem();
        candidateModel.setRowCount(0);
        for (Component c : allCandidates) {
            if (!matchType(c, type)) continue;
            String label = (c.getCode() == null ? "" : c.getCode()) + " " +
                           (c.getName() == null ? "" : c.getName()) + " " +
                           (c.getSpec() == null ? "" : c.getSpec());
            if (kw.isEmpty() || label.toLowerCase().contains(kw)) {
                candidateModel.addRow(new Object[]{
                    c.getId(), typeLabel(c.getType()), c.getCode(), c.getName(), c.getSpec(), c.getUnit()
                });
            }
        }
    }

    private boolean matchType(Component c, String filter) {
        if (filter == null || "全部".equals(filter)) return true;
        if ("成品".equals(filter)) return Component.TYPE_PRODUCT.equals(c.getType());
        if ("半成品".equals(filter)) return Component.TYPE_SEMI.equals(c.getType());
        if ("零件".equals(filter)) return Component.TYPE_PART.equals(c.getType());
        if ("外购件".equals(filter)) return Component.TYPE_PURCHASE.equals(c.getType());
        return true;
    }

    private void addSelectedToPicked() {
        int[] rows = candidateTable.getSelectedRows();
        if (rows.length == 0) return;
        for (int viewRow : rows) {
            int modelRow = candidateTable.convertRowIndexToModel(viewRow);
            Long id = (Long) candidateModel.getValueAt(modelRow, 0);
            if (!pickedQuantities.containsKey(id)) {
                pickedQuantities.put(id, 1.0);
                pickedModel.addRow(new Object[]{
                    id,
                    candidateModel.getValueAt(modelRow, 1),
                    candidateModel.getValueAt(modelRow, 2),
                    candidateModel.getValueAt(modelRow, 3),
                    candidateModel.getValueAt(modelRow, 5),
                    "1"
                });
            }
        }
        updatePickedCount();
        clearResult();
    }

    private void removePicked() {
        int[] rows = pickedTable.getSelectedRows();
        if (rows.length == 0) return;
        // 从大到小删除，避免索引错位
        Integer[] modelRows = new Integer[rows.length];
        for (int i = 0; i < rows.length; i++) modelRows[i] = pickedTable.convertRowIndexToModel(rows[i]);
        java.util.Arrays.sort(modelRows, (a, b) -> b - a);
        for (int mr : modelRows) {
            Long id = (Long) pickedModel.getValueAt(mr, 0);
            pickedQuantities.remove(id);
            pickedModel.removeRow(mr);
        }
        updatePickedCount();
        clearResult();
    }

    private void updatePickedCount() {
        pickedCount.setText("已选 " + pickedQuantities.size() + " 项");
    }

    private void updateResultCount() {
        resultCount.setText("共 " + resultModel.getRowCount() + " 行");
    }

    private void clearResult() {
        resultModel.setRowCount(0);
        currentRows = new ArrayList<>();
        updateResultCount();
    }

    private void generateBom() {
        if (pickedQuantities.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先加入至少一个成品 / 半成品 / 零件");
            return;
        }
        List<BomRequest> requests = collectPickedRequests();
        if (requests.isEmpty()) return;
        runSummarizeAsync(requests);
    }

    private List<BomRequest> collectPickedRequests() {
        if (pickedTable.isEditing()) {
            pickedTable.getCellEditor().stopCellEditing();
        }

        List<BomRequest> requests = new ArrayList<>();
        Map<Long, Double> parsedQuantities = new LinkedHashMap<>();
        for (int i = 0; i < pickedModel.getRowCount(); i++) {
            Long id = (Long) pickedModel.getValueAt(i, 0);
            double qty;
            try {
                qty = Double.parseDouble(String.valueOf(pickedModel.getValueAt(i, 5)).trim());
                if (qty <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "第 " + (i + 1) + " 行数量无效，请输入大于 0 的数字");
                return new ArrayList<>();
            }
            parsedQuantities.put(id, qty);
            requests.add(new BomRequest(id, qty));
        }
        pickedQuantities.clear();
        pickedQuantities.putAll(parsedQuantities);
        updatePickedCount();
        return requests;
    }

    private void runSummarizeAsync(List<BomRequest> requests) {
        generateBtn.setEnabled(false);
        generateBtn.setText("汇总中…");
        progressBar.setVisible(true);

        new SwingWorker<List<BomSummaryRow>, Void>() {
            @Override
            protected List<BomSummaryRow> doInBackground() throws Exception {
                return bomService.summarizeWithQuantities(requests);
            }
            @Override
            protected void done() {
                progressBar.setVisible(false);
                generateBtn.setEnabled(true);
                generateBtn.setText("生成 BOM 汇总 →");
                try {
                    List<BomSummaryRow> rows = get();
                    currentRows = rows;
                    resultModel.setRowCount(0);
                    for (BomSummaryRow row : rows) {
                        resultModel.addRow(new Object[]{
                            row.code, row.name, row.spec, row.material, row.unit,
                            formatQty(row.totalQty), formatQty(row.stockQty), formatQty(row.deductedQty),
                            formatQty(row.shortageQty), row.stockRemark
                        });
                    }
                    updateResultCount();
                    if (rows.isEmpty()) {
                        JOptionPane.showMessageDialog(BomPanel.this, "所选组件没有 BOM 子件");
                    }
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(BomPanel.this, "BOM 展开失败: " + msg);
                }
            }
        }.execute();
    }

    private void exportCsv() {
        if (currentRows.isEmpty()) { JOptionPane.showMessageDialog(this, "请先生成 BOM 汇总"); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件", "csv"));
        chooser.setSelectedFile(new File("BOM汇总清单.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".csv")) file = new File(file.getAbsolutePath() + ".csv");
            try {
                bomService.exportCsv(currentRows, file);
                JOptionPane.showMessageDialog(this, "导出成功: " + file.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage());
            }
        }
    }

    private void exportExcel() {
        if (currentRows.isEmpty()) { JOptionPane.showMessageDialog(this, "请先生成 BOM 汇总"); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件", "xlsx"));
        chooser.setSelectedFile(new File("BOM汇总清单.xlsx"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".xlsx")) file = new File(file.getAbsolutePath() + ".xlsx");
            try {
                bomService.exportExcel(currentRows, file);
                JOptionPane.showMessageDialog(this, "导出成功: " + file.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage());
            }
        }
    }

    private String formatQty(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) {
            return String.valueOf((long) qty);
        }
        return String.valueOf(qty);
    }
}
