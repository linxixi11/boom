package com.bom.ui;

import com.bom.dao.ComponentDao;
import com.bom.model.Component;
import com.bom.service.BomService;
import com.bom.service.BomService.BomRequest;
import com.bom.service.BomService.BomSummaryRow;
import com.bom.service.OrderService;
import com.bom.service.OrderService.OrderPick;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderDraftDialog extends JDialog {
    private final ComponentDao componentDao = new ComponentDao();
    private final BomService bomService = new BomService();
    private final OrderService orderService = new OrderService();
    private final Runnable afterSaved;

    private final JTextField projectNameField = new JTextField(18);
    private final JTextField searchField = new JTextField(14);
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[]{"全部", "成品", "半成品", "零件", "外购件"});
    private final DefaultTableModel candidateModel;
    private final JTable candidateTable;

    private final DefaultTableModel pickedModel;
    private final JTable pickedTable;
    private final Map<Long, Double> pickedQuantities = new LinkedHashMap<>();

    private final DefaultTableModel resultModel;
    private final JTable resultTable;
    private final TableRowSorter<DefaultTableModel> resultSorter;
    private final JLabel resultCount = UIStyle.hintLabel("共 0 行");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton generateBtn = UIStyle.primaryButton("生成明细");
    private final javax.swing.Timer summarizeTimer;

    private List<Component> allCandidates = new ArrayList<>();
    private List<BomSummaryRow> currentRows = new ArrayList<>();
    private boolean syncingPicked = false;
    private boolean generating = false;

    public static void show(java.awt.Component owner, Runnable afterSaved) {
        Window window = SwingUtilities.getWindowAncestor(owner);
        OrderDraftDialog dialog = new OrderDraftDialog(window, afterSaved);
        dialog.setVisible(true);
    }

    private OrderDraftDialog(Window owner, Runnable afterSaved) {
        super(owner, "新建订单确认", ModalityType.APPLICATION_MODAL);
        this.afterSaved = afterSaved;
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(UIStyle.BG);
        UIStyle.rememberWindowBounds(this, "dialog.order.draft.bounds", new Dimension(1180, 700), owner);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.setOpaque(false);
        top.add(new JLabel("项目名称"));
        top.add(projectNameField);
        top.add(new JLabel("类型"));
        typeFilter.setFont(UIStyle.FONT);
        top.add(typeFilter);
        top.add(new JLabel("搜索"));
        searchField.setFont(UIStyle.FONT);
        top.add(searchField);
        add(top, BorderLayout.NORTH);

        candidateModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "规格", "单位"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        candidateTable = UIStyle.createTable(candidateModel);
        UIStyle.hideColumn(candidateTable, 0);
        candidateTable.getColumnModel().getColumn(1).setMaxWidth(70);

        JPanel candidatePanel = UIStyle.section();
        candidatePanel.add(UIStyle.sectionLabel("可选物料"), BorderLayout.NORTH);
        candidatePanel.add(UIStyle.wrap(candidateTable), BorderLayout.CENTER);
        JButton addBtn = UIStyle.button("加入订单");
        candidatePanel.add(UIStyle.buttonRow(addBtn), BorderLayout.SOUTH);

        pickedModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "单位", "数量"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 5; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        pickedTable = UIStyle.createTable(pickedModel);
        pickedTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        UIStyle.hideColumn(pickedTable, 0);
        pickedTable.getColumnModel().getColumn(1).setMaxWidth(70);

        JPanel pickedPanel = UIStyle.section();
        pickedPanel.add(UIStyle.sectionLabel("订单选择"), BorderLayout.NORTH);
        pickedPanel.add(UIStyle.wrap(pickedTable), BorderLayout.CENTER);
        JButton removeBtn = UIStyle.button("移除选中");
        JButton clearBtn = UIStyle.button("清空");
        pickedPanel.add(UIStyle.buttonRow(removeBtn, clearBtn, generateBtn), BorderLayout.SOUTH);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, candidatePanel, pickedPanel);
        leftSplit.setResizeWeight(0.52);
        leftSplit.setBorder(null);
        leftSplit.setDividerSize(8);
        UIStyle.rememberDividerLocation(leftSplit, "order.draft.left.split", 300);

        resultModel = new DefaultTableModel(
            new String[]{"序号", "类型", "物料编号", "物料名称", "规格型号", "材质", "单位", "总需求", "库存数量", "扣库存", "需补数量", "库存备注"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultTable = UIStyle.createTable(resultModel);
        resultSorter = new TableRowSorter<>(resultModel);
        resultTable.setRowSorter(resultSorter);
        resultTable.getColumnModel().getColumn(0).setMaxWidth(52);
        resultTable.getColumnModel().getColumn(1).setMaxWidth(70);

        JPanel resultPanel = UIStyle.section();
        JPanel resultHeader = new JPanel(new BorderLayout());
        resultHeader.setOpaque(false);
        resultHeader.add(UIStyle.sectionLabel("订单明细确认"), BorderLayout.WEST);
        resultHeader.add(resultCount, BorderLayout.EAST);
        resultPanel.add(resultHeader, BorderLayout.NORTH);
        resultPanel.add(UIStyle.wrap(resultTable), BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, resultPanel);
        mainSplit.setResizeWeight(0.38);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        UIStyle.rememberDividerLocation(mainSplit, "order.draft.main.split", 450);
        add(mainSplit, BorderLayout.CENTER);

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(160, 22));
        JButton exportBtn = UIStyle.button("导出 Excel");
        JButton saveBtn = UIStyle.primaryButton("保存订单");
        JButton closeBtn = UIStyle.button("关闭");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.setOpaque(false);
        bottom.add(progressBar);
        bottom.add(exportBtn);
        bottom.add(saveBtn);
        bottom.add(closeBtn);
        add(bottom, BorderLayout.SOUTH);

        summarizeTimer = new javax.swing.Timer(350, e -> generateSummary(false));
        summarizeTimer.setRepeats(false);

        DocumentListener filterListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyCandidateFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyCandidateFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyCandidateFilter(); }
        };
        searchField.getDocument().addDocumentListener(filterListener);
        typeFilter.addActionListener(e -> applyCandidateFilter());
        addBtn.addActionListener(e -> addSelectedCandidates());
        candidateTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addSelectedCandidates();
            }
        });
        removeBtn.addActionListener(e -> removePicked());
        clearBtn.addActionListener(e -> clearDraft());
        generateBtn.addActionListener(e -> generateSummary(true));
        exportBtn.addActionListener(e -> exportExcel());
        saveBtn.addActionListener(e -> saveOrder());
        closeBtn.addActionListener(e -> dispose());
        pickedModel.addTableModelListener(e -> {
            if (!syncingPicked && e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getColumn() == 5) {
                updatePickedQuantity(e.getFirstRow());
            }
        });

        refreshCandidates();
    }

    private void refreshCandidates() {
        try {
            allCandidates = new ArrayList<>();
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PRODUCT));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_SEMI));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PART));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PURCHASE));
            applyCandidateFilter();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载候选失败: " + e.getMessage());
        }
    }

    private void applyCandidateFilter() {
        String keyword = searchField.getText().trim().toLowerCase();
        String type = (String) typeFilter.getSelectedItem();
        candidateModel.setRowCount(0);
        for (Component c : allCandidates) {
            if (!matchType(c, type)) continue;
            String text = safe(c.getCode()) + " " + safe(c.getName()) + " " + safe(c.getSpec());
            if (keyword.isEmpty() || text.toLowerCase().contains(keyword)) {
                candidateModel.addRow(new Object[]{c.getId(), typeLabel(c.getType()), c.getCode(), c.getName(), c.getSpec(), c.getUnit()});
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

    private void addSelectedCandidates() {
        int[] rows = candidateTable.getSelectedRows();
        if (rows.length == 0) return;
        syncingPicked = true;
        try {
            for (int viewRow : rows) {
                int modelRow = candidateTable.convertRowIndexToModel(viewRow);
                Long id = (Long) candidateModel.getValueAt(modelRow, 0);
                if (pickedQuantities.containsKey(id)) continue;
                pickedQuantities.put(id, 1.0);
                pickedModel.addRow(new Object[]{
                    id, candidateModel.getValueAt(modelRow, 1), candidateModel.getValueAt(modelRow, 2),
                    candidateModel.getValueAt(modelRow, 3), candidateModel.getValueAt(modelRow, 5), "1"
                });
                int newRow = pickedModel.getRowCount() - 1;
                int viewPicked = pickedTable.convertRowIndexToView(newRow);
                pickedTable.setRowSelectionInterval(viewPicked, viewPicked);
                pickedTable.scrollRectToVisible(pickedTable.getCellRect(viewPicked, 0, true));
            }
        } finally {
            syncingPicked = false;
        }
        scheduleSummary();
    }

    private void removePicked() {
        int[] rows = pickedTable.getSelectedRows();
        if (rows.length == 0) return;
        Integer[] modelRows = new Integer[rows.length];
        for (int i = 0; i < rows.length; i++) modelRows[i] = pickedTable.convertRowIndexToModel(rows[i]);
        java.util.Arrays.sort(modelRows, (a, b) -> b - a);
        syncingPicked = true;
        try {
            for (int row : modelRows) {
                pickedQuantities.remove((Long) pickedModel.getValueAt(row, 0));
                pickedModel.removeRow(row);
            }
        } finally {
            syncingPicked = false;
        }
        scheduleSummary();
    }

    private void updatePickedQuantity(int row) {
        if (row < 0 || row >= pickedModel.getRowCount()) return;
        try {
            double qty = Double.parseDouble(String.valueOf(pickedModel.getValueAt(row, 5)).trim());
            if (qty > 0) {
                pickedQuantities.put((Long) pickedModel.getValueAt(row, 0), qty);
                scheduleSummary();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void scheduleSummary() {
        currentRows = new ArrayList<>();
        resultModel.setRowCount(0);
        updateResultCount();
        if (!pickedQuantities.isEmpty()) summarizeTimer.restart();
    }

    private void generateSummary(boolean showErrors) {
        if (generating || pickedQuantities.isEmpty()) return;
        List<BomRequest> requests = collectRequests(showErrors);
        if (requests.isEmpty()) return;
        generating = true;
        generateBtn.setEnabled(false);
        progressBar.setVisible(true);
        new SwingWorker<List<BomSummaryRow>, Void>() {
            @Override protected List<BomSummaryRow> doInBackground() throws Exception {
                return bomService.summarizeWithQuantities(requests);
            }
            @Override protected void done() {
                generating = false;
                generateBtn.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    currentRows = get();
                    resultModel.setRowCount(0);
                    for (BomSummaryRow row : currentRows) {
                        resultModel.addRow(new Object[]{
                            row.sequence, BomService.typeLabel(row.type), row.code, row.name, row.spec, row.material, row.unit,
                            formatQty(row.totalQty), formatQty(row.stockQty), formatQty(row.deductedQty),
                            formatQty(row.shortageQty), row.stockRemark
                        });
                    }
                    updateResultCount();
                } catch (Exception e) {
                    String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
                    JOptionPane.showMessageDialog(OrderDraftDialog.this, "生成订单明细失败: " + message);
                }
            }
        }.execute();
    }

    private List<BomRequest> collectRequests(boolean showErrors) {
        if (pickedTable.isEditing()) pickedTable.getCellEditor().stopCellEditing();
        List<BomRequest> requests = new ArrayList<>();
        for (int i = 0; i < pickedModel.getRowCount(); i++) {
            try {
                Long id = (Long) pickedModel.getValueAt(i, 0);
                double qty = Double.parseDouble(String.valueOf(pickedModel.getValueAt(i, 5)).trim());
                if (qty <= 0) throw new NumberFormatException();
                pickedQuantities.put(id, qty);
                requests.add(new BomRequest(id, qty));
            } catch (NumberFormatException e) {
                if (showErrors) JOptionPane.showMessageDialog(this, "第 " + (i + 1) + " 行数量无效");
                return new ArrayList<>();
            }
        }
        return requests;
    }

    private List<OrderPick> collectPicks() {
        List<OrderPick> picks = new ArrayList<>();
        for (Map.Entry<Long, Double> e : pickedQuantities.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                picks.add(new OrderPick(e.getKey(), e.getValue()));
            }
        }
        return picks;
    }

    private void saveOrder() {
        if (currentRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先生成订单明细");
            return;
        }
        String projectName = projectNameField.getText().trim();
        if (projectName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "项目名称不能为空");
            return;
        }
        try {
            orderService.saveOrder(projectName, currentRows, collectPicks());
            if (afterSaved != null) afterSaved.run();
            dispose();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "保存订单失败: " + e.getMessage());
        }
    }

    private void exportExcel() {
        if (currentRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先生成订单明细");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件", "xlsx"));
        chooser.setSelectedFile(new File("订单明细.xlsx"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".xlsx")) file = new File(file.getAbsolutePath() + ".xlsx");
            try {
                bomService.exportExcel(currentRows, file, projectNameField.getText().trim());
                JOptionPane.showMessageDialog(this, "导出成功: " + file.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "导出失败: " + e.getMessage());
            }
        }
    }

    private void clearDraft() {
        pickedQuantities.clear();
        pickedModel.setRowCount(0);
        currentRows = new ArrayList<>();
        resultModel.setRowCount(0);
        updateResultCount();
    }

    private void updateResultCount() {
        resultCount.setText("共 " + resultModel.getRowCount() + " 行");
    }

    private static String typeLabel(String type) {
        if (Component.TYPE_PRODUCT.equals(type)) return "成品";
        if (Component.TYPE_SEMI.equals(type)) return "半成品";
        if (Component.TYPE_PART.equals(type)) return "零件";
        if (Component.TYPE_PURCHASE.equals(type)) return "外购件";
        return type == null ? "" : type;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatQty(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) return String.valueOf((long) qty);
        return String.valueOf(qty);
    }
}
