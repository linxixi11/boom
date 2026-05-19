package com.bom.ui;

import com.bom.dao.ComponentDao;
import com.bom.model.Component;
import com.bom.service.BomService;
import com.bom.service.BomService.BomRequest;
import com.bom.service.BomService.BomSummaryRow;
import com.bom.service.OrderService;
import com.bom.service.OrderService.Order;
import com.bom.service.OrderService.OrderItem;
import com.bom.service.OrderService.OrderPick;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单页：左侧历史订单列表；右侧像 BOM 汇总一样可编辑——
 * 选中订单后载入其原始选择项，可加候选、改数量，明细实时重算，
 * 可导出 Excel，并以「保存修改」覆盖原订单。
 */
public class OrderPanel extends JPanel {
    private final OrderService orderService = new OrderService();
    private final BomService bomService = new BomService();
    private final ComponentDao componentDao = new ComponentDao();

    private final DefaultTableModel orderModel;
    private final JTable orderTable;

    private final DefaultTableModel candidateModel;
    private final JTable candidateTable;
    private final JTextField candSearchField;
    private final JComboBox<String> candTypeFilter;

    private final DefaultTableModel pickedModel;
    private final JTable pickedTable;
    private final JLabel pickedCount;
    private final Map<Long, Double> pickedQuantities = new LinkedHashMap<>();

    private final DefaultTableModel resultModel;
    private final JTable resultTable;
    private final TableRowSorter<DefaultTableModel> resultSorter;
    private final JTextField projectNameField;
    private final JTextField resultSearchField;
    private final JComboBox<String> resultTypeFilter;
    private final JLabel resultCount;
    private final JProgressBar progressBar;
    private final JButton generateBtn;
    private final javax.swing.Timer summarizeTimer;

    private List<Component> allCandidates = new ArrayList<>();
    private List<BomSummaryRow> currentRows = new ArrayList<>();
    private PageFormat pageFormat = TablePrintSupport.defaultLandscapeA4();
    private Long loadedOrderId = null;
    private boolean syncingPicked = false;
    private boolean generating = false;

    public OrderPanel() {
        setLayout(new BorderLayout());
        setBackground(UIStyle.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // ====== 左：历史订单 ======
        JPanel leftPanel = UIStyle.section();
        leftPanel.add(UIStyle.sectionLabel("历史订单"), BorderLayout.NORTH);
        orderModel = new DefaultTableModel(new String[]{"ID", "项目名称", "创建时间"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        orderTable = UIStyle.createTable(orderModel);
        UIStyle.hideColumn(orderTable, 0);
        orderTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        orderTable.getColumnModel().getColumn(2).setMaxWidth(150);
        leftPanel.add(UIStyle.wrap(orderTable), BorderLayout.CENTER);
        JButton newOrderBtn = UIStyle.primaryButton("新建订单");
        JButton deleteBtn = UIStyle.button("删除订单");
        leftPanel.add(UIStyle.buttonRow(newOrderBtn, deleteBtn), BorderLayout.SOUTH);

        // ====== 候选物料 ======
        JPanel candPanel = UIStyle.section();
        JPanel candHeader = new JPanel(new BorderLayout(8, 0));
        candHeader.setOpaque(false);
        candHeader.add(UIStyle.sectionLabel("候选成品 / 半成品 / 零件 / 外购件"), BorderLayout.WEST);
        JPanel candFilter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        candFilter.setOpaque(false);
        candTypeFilter = new JComboBox<>(new String[]{"全部", "成品", "半成品", "零件", "外购件"});
        candTypeFilter.setFont(UIStyle.FONT);
        candSearchField = new JTextField(12);
        candSearchField.setFont(UIStyle.FONT);
        candSearchField.setToolTipText("搜索编号 / 名称 / 规格");
        candFilter.add(new JLabel("类型"));
        candFilter.add(candTypeFilter);
        candFilter.add(new JLabel("搜索"));
        candFilter.add(candSearchField);
        candHeader.add(candFilter, BorderLayout.EAST);
        candPanel.add(candHeader, BorderLayout.NORTH);

        candidateModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "规格", "单位"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        candidateTable = UIStyle.createTable(candidateModel);
        UIStyle.hideColumn(candidateTable, 0);
        candidateTable.getColumnModel().getColumn(1).setMaxWidth(70);
        candPanel.add(UIStyle.wrap(candidateTable), BorderLayout.CENTER);
        JButton addBtn = UIStyle.primaryButton("加入订单 ↓");
        JPanel candFooter = new JPanel(new BorderLayout());
        candFooter.setOpaque(false);
        candFooter.add(UIStyle.buttonRow(addBtn), BorderLayout.WEST);
        candFooter.add(UIStyle.hintLabel("双击候选行也可加入"), BorderLayout.EAST);
        candPanel.add(candFooter, BorderLayout.SOUTH);

        // ====== 已选（订单原始项） ======
        JPanel pickedPanel = UIStyle.section();
        pickedCount = UIStyle.hintLabel("已选 0 项");
        JPanel pickedHeader = new JPanel(new BorderLayout());
        pickedHeader.setOpaque(false);
        pickedHeader.add(UIStyle.sectionLabel("订单选择项（可改数量）"), BorderLayout.WEST);
        pickedHeader.add(pickedCount, BorderLayout.EAST);
        pickedPanel.add(pickedHeader, BorderLayout.NORTH);
        pickedModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "单位", "数量"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 5; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        pickedTable = UIStyle.createTable(pickedModel);
        pickedTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        UIStyle.hideColumn(pickedTable, 0);
        pickedTable.getColumnModel().getColumn(1).setMaxWidth(70);
        pickedPanel.add(UIStyle.wrap(pickedTable), BorderLayout.CENTER);
        JButton removeBtn = UIStyle.button("移除选中");
        JButton clearBtn = UIStyle.button("清空");
        generateBtn = UIStyle.primaryButton("重新汇总 →");
        pickedPanel.add(UIStyle.buttonRow(removeBtn, clearBtn, generateBtn), BorderLayout.SOUTH);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, candPanel, pickedPanel);
        leftSplit.setResizeWeight(0.55);
        leftSplit.setBorder(null);
        leftSplit.setDividerSize(8);
        leftSplit.setOpaque(false);
        UIStyle.rememberDividerLocation(leftSplit, "order.left.split", 300);

        // ====== 订单明细（汇总结果） ======
        JPanel resultPanel = UIStyle.section();
        resultCount = UIStyle.hintLabel("共 0 行");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("汇总中…");
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(0, 20));
        progressBar.setVisible(false);
        JPanel resultHeader = new JPanel(new BorderLayout(6, 0));
        resultHeader.setOpaque(false);
        JPanel resultTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        resultTitle.setOpaque(false);
        resultTitle.add(UIStyle.sectionLabel("订单明细"));
        resultTitle.add(new JLabel("项目名称"));
        projectNameField = new JTextField(14);
        projectNameField.setFont(UIStyle.FONT);
        resultTitle.add(projectNameField);
        resultHeader.add(resultTitle, BorderLayout.WEST);
        JPanel resultEast = new JPanel(new BorderLayout(8, 0));
        resultEast.setOpaque(false);
        resultEast.add(progressBar, BorderLayout.CENTER);
        resultEast.add(resultCount, BorderLayout.EAST);
        resultHeader.add(resultEast, BorderLayout.EAST);
        resultPanel.add(resultHeader, BorderLayout.NORTH);

        resultModel = new DefaultTableModel(
            new String[]{"序号", "类型", "物料编号", "物料名称", "规格型号", "材质", "单位", "总需求", "库存数量", "扣库存", "需补数量", "库存备注"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultTable = UIStyle.createTable(resultModel);
        resultSorter = new TableRowSorter<>(resultModel);
        resultTable.setRowSorter(resultSorter);
        resultTable.getColumnModel().getColumn(0).setMaxWidth(52);
        resultTable.getColumnModel().getColumn(1).setMaxWidth(70);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        JPanel resultCenter = new JPanel(new BorderLayout(6, 6));
        resultCenter.setOpaque(false);
        JPanel resultFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        resultFilter.setOpaque(false);
        resultTypeFilter = new JComboBox<>(new String[]{"全部", "成品", "半成品", "零件", "外购件"});
        resultTypeFilter.setFont(UIStyle.FONT);
        resultSearchField = new JTextField(16);
        resultSearchField.setFont(UIStyle.FONT);
        resultSearchField.setToolTipText("筛选结果中的编号 / 名称 / 规格 / 材质");
        resultFilter.add(new JLabel("筛选类型"));
        resultFilter.add(resultTypeFilter);
        resultFilter.add(new JLabel("关键字"));
        resultFilter.add(resultSearchField);
        resultCenter.add(resultFilter, BorderLayout.NORTH);
        resultCenter.add(UIStyle.wrap(resultTable), BorderLayout.CENTER);
        resultPanel.add(resultCenter, BorderLayout.CENTER);

        JButton exportExcelBtn = UIStyle.button("导出 Excel");
        JButton pageSetupBtn = UIStyle.button("页面设置");
        JButton previewBtn = UIStyle.button("打印预览");
        JButton printBtn = UIStyle.button("打印");
        JButton saveBtn = UIStyle.primaryButton("保存修改（覆盖原订单）");
        resultPanel.add(UIStyle.buttonRow(exportExcelBtn, pageSetupBtn, previewBtn, printBtn, saveBtn), BorderLayout.SOUTH);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, resultPanel);
        editorSplit.setResizeWeight(0.42);
        editorSplit.setBorder(null);
        editorSplit.setDividerSize(8);
        editorSplit.setOpaque(false);
        UIStyle.rememberDividerLocation(editorSplit, "order.editor.split", 460);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, editorSplit);
        mainSplit.setResizeWeight(0.2);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        mainSplit.setOpaque(false);
        UIStyle.rememberDividerLocation(mainSplit, "order.main.split", 250);
        add(mainSplit, BorderLayout.CENTER);

        // ====== 事件 ======
        summarizeTimer = new javax.swing.Timer(350, e -> generateSummary(false));
        summarizeTimer.setRepeats(false);

        orderTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) maybeLoadSelectedOrder();
        });
        newOrderBtn.addActionListener(e -> OrderDraftDialog.show(this, this::refreshData));
        deleteBtn.addActionListener(e -> deleteSelectedOrder());

        DocumentListener candListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyCandidateFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyCandidateFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyCandidateFilter(); }
        };
        candSearchField.getDocument().addDocumentListener(candListener);
        candTypeFilter.addActionListener(e -> applyCandidateFilter());
        addBtn.addActionListener(e -> addSelectedCandidates());
        candidateTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addSelectedCandidates();
            }
        });
        removeBtn.addActionListener(e -> removePicked());
        clearBtn.addActionListener(e -> clearPicked());
        generateBtn.addActionListener(e -> generateSummary(true));
        pickedModel.addTableModelListener(e -> {
            if (!syncingPicked && e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getColumn() == 5) {
                updatePickedQuantity(e.getFirstRow());
            }
        });

        DocumentListener resultListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyResultFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyResultFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyResultFilter(); }
        };
        resultSearchField.getDocument().addDocumentListener(resultListener);
        resultTypeFilter.addActionListener(e -> applyResultFilter());

        exportExcelBtn.addActionListener(e -> exportExcel());
        pageSetupBtn.addActionListener(e -> pageFormat = TablePrintSupport.showPageSetup(this, pageFormat));
        previewBtn.addActionListener(e -> TablePrintSupport.showPreview(this, resultTable, pageFormat, printTitle()));
        printBtn.addActionListener(e -> TablePrintSupport.print(this, resultTable, pageFormat, printTitle()));
        saveBtn.addActionListener(e -> saveChanges());

        refreshData();
    }

    public void refreshData() {
        Long keep = loadedOrderId;
        try {
            allCandidates = new ArrayList<>();
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PRODUCT));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_SEMI));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PART));
            allCandidates.addAll(componentDao.findByType(Component.TYPE_PURCHASE));
            applyCandidateFilter();

            List<Order> orders = orderService.getAllOrders();
            orderModel.setRowCount(0);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            int selectRow = -1;
            for (int i = 0; i < orders.size(); i++) {
                Order o = orders.get(i);
                orderModel.addRow(new Object[]{o.id, o.projectName, sdf.format(o.createdAt)});
                if (keep != null && keep.equals(o.id)) selectRow = i;
            }
            if (selectRow >= 0) {
                int viewRow = orderTable.convertRowIndexToView(selectRow);
                orderTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            } else if (orderModel.getRowCount() > 0 && loadedOrderId == null) {
                orderTable.setRowSelectionInterval(0, 0);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载订单失败: " + e.getMessage());
        }
    }

    private void maybeLoadSelectedOrder() {
        int row = orderTable.getSelectedRow();
        if (row < 0) return;
        Long orderId = (Long) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 0);
        if (orderId.equals(loadedOrderId)) return;
        loadSelectedOrder(orderId);
    }

    private void loadSelectedOrder(Long orderId) {
        int row = orderTable.getSelectedRow();
        loadedOrderId = orderId;
        projectNameField.setText(row < 0 ? "" :
            String.valueOf(orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 1)));
        pickedQuantities.clear();
        syncingPicked = true;
        pickedModel.setRowCount(0);
        syncingPicked = false;
        currentRows = new ArrayList<>();
        resultModel.setRowCount(0);
        try {
            List<OrderPick> picks = orderService.getOrderPicks(orderId);
            if (!picks.isEmpty()) {
                Map<Long, Component> byId = new LinkedHashMap<>();
                for (Component c : allCandidates) byId.put(c.getId(), c);
                syncingPicked = true;
                for (OrderPick p : picks) {
                    Component c = byId.get(p.componentId);
                    if (c == null) continue;
                    pickedQuantities.put(p.componentId, p.quantity);
                    pickedModel.addRow(new Object[]{
                        c.getId(), typeLabel(c.getType()), c.getCode(), c.getName(),
                        c.getUnit(), formatQty(p.quantity)});
                }
                syncingPicked = false;
                updatePickedCount();
                generateSummary(false);
            } else {
                // 旧订单没有保存原始选择项：直接展示已保存明细，仍可导出/打印
                updatePickedCount();
                loadStoredItemsAsResult(orderId);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载订单失败: " + e.getMessage());
        }
    }

    private void loadStoredItemsAsResult(Long orderId) throws SQLException {
        List<OrderItem> items = orderService.getOrderItems(orderId);
        currentRows = new ArrayList<>();
        int seq = 1;
        for (OrderItem it : items) {
            currentRows.add(new BomSummaryRow(seq++, null, it.componentType, "", it.componentCode,
                it.componentName, it.spec, it.material, it.unit, it.totalQty,
                it.stockQty, it.deductedQty, it.shortageQty, it.stockRemark));
        }
        renderResult();
        if (!items.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "该订单是旧版本，未保存原始选择项，无法自动重算。\n如需编辑，请在左侧加入物料后点「保存修改」覆盖。");
        }
    }

    private void applyCandidateFilter() {
        String kw = candSearchField.getText().trim().toLowerCase();
        String type = (String) candTypeFilter.getSelectedItem();
        candidateModel.setRowCount(0);
        for (Component c : allCandidates) {
            if (!matchType(c, type)) continue;
            String label = safe(c.getCode()) + " " + safe(c.getName()) + " " + safe(c.getSpec());
            if (kw.isEmpty() || label.toLowerCase().contains(kw)) {
                candidateModel.addRow(new Object[]{
                    c.getId(), typeLabel(c.getType()), c.getCode(), c.getName(), c.getSpec(), c.getUnit()});
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
                    candidateModel.getValueAt(modelRow, 3), candidateModel.getValueAt(modelRow, 5), "1"});
            }
        } finally {
            syncingPicked = false;
        }
        updatePickedCount();
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
            for (int mr : modelRows) {
                pickedQuantities.remove((Long) pickedModel.getValueAt(mr, 0));
                pickedModel.removeRow(mr);
            }
        } finally {
            syncingPicked = false;
        }
        updatePickedCount();
        scheduleSummary();
    }

    private void clearPicked() {
        syncingPicked = true;
        pickedModel.setRowCount(0);
        syncingPicked = false;
        pickedQuantities.clear();
        updatePickedCount();
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

    private void updatePickedCount() {
        pickedCount.setText("已选 " + pickedQuantities.size() + " 项");
    }

    private void scheduleSummary() {
        currentRows = new ArrayList<>();
        resultModel.setRowCount(0);
        updateResultCount();
        if (!pickedQuantities.isEmpty()) summarizeTimer.restart();
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
                if (showErrors) JOptionPane.showMessageDialog(this, "第 " + (i + 1) + " 行数量无效，请输入大于 0 的数字");
                return new ArrayList<>();
            }
        }
        return requests;
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
                    renderResult();
                    if (currentRows.isEmpty() && showErrors) {
                        JOptionPane.showMessageDialog(OrderPanel.this, "所选组件没有 BOM 子件");
                    }
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(OrderPanel.this, "重新汇总失败: " + msg);
                }
            }
        }.execute();
    }

    private void renderResult() {
        resultModel.setRowCount(0);
        for (BomSummaryRow row : currentRows) {
            resultModel.addRow(new Object[]{
                row.sequence, BomService.typeLabel(row.type), row.code, row.name, row.spec, row.material, row.unit,
                formatQty(row.totalQty), formatQty(row.stockQty), formatQty(row.deductedQty),
                formatQty(row.shortageQty), row.stockRemark});
        }
        applyResultFilter();
        updateResultCount();
    }

    private void applyResultFilter() {
        String type = (String) resultTypeFilter.getSelectedItem();
        String keyword = resultSearchField.getText().trim().toLowerCase();
        List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();
        if (type != null && !"全部".equals(type)) {
            filters.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(type) + "$", 1));
        }
        if (!keyword.isEmpty()) {
            filters.add(new RowFilter<DefaultTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    for (int i = 2; i <= 5; i++) {
                        Object value = entry.getValue(i);
                        if (value != null && value.toString().toLowerCase().contains(keyword)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        resultSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        for (int i = 0; i < resultTable.getRowCount(); i++) {
            int modelRow = resultTable.convertRowIndexToModel(i);
            resultModel.setValueAt(i + 1, modelRow, 0);
        }
        updateResultCount();
    }

    private void updateResultCount() {
        resultCount.setText("显示 " + resultTable.getRowCount() + " / 共 " + resultModel.getRowCount() + " 行");
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

    private void saveChanges() {
        if (loadedOrderId == null) {
            JOptionPane.showMessageDialog(this, "请先在左侧选择要修改的订单，或点「新建订单」");
            return;
        }
        String projectName = projectNameField.getText().trim();
        if (projectName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "项目名称不能为空");
            return;
        }
        if (!pickedQuantities.isEmpty()) {
            List<BomRequest> requests = collectRequests(true);
            if (requests.isEmpty()) return;
            // 最后确认前同步重算一次，确保保存的就是当前选择项展开的最新明细
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                currentRows = bomService.summarizeWithQuantities(requests);
                renderResult();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "重新汇总失败: " + e.getMessage());
                return;
            } finally {
                setCursor(Cursor.getDefaultCursor());
            }
        }
        if (currentRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可保存的明细，请先加入物料并重新汇总");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认用当前明细覆盖原订单 [" + projectName + "] 吗？\n库存将按新明细重新扣减。",
            "保存修改", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            orderService.updateOrder(loadedOrderId, projectName, currentRows, collectPicks());
            JOptionPane.showMessageDialog(this, "已保存，订单已更新");
            refreshData();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage());
        }
    }

    private void deleteSelectedOrder() {
        int row = orderTable.getSelectedRow();
        if (row < 0) return;
        Long orderId = (Long) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 0);
        String pName = String.valueOf(orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 1));
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认删除订单 [" + pName + "] 吗？", "删除订单", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            orderService.deleteOrder(orderId);
            if (orderId.equals(loadedOrderId)) {
                loadedOrderId = null;
                clearPicked();
                projectNameField.setText("");
            }
            refreshData();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "删除失败: " + e.getMessage());
        }
    }

    private void exportExcel() {
        if (currentRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可导出的订单明细");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件", "xlsx"));
        String pn = projectNameField.getText().trim();
        chooser.setSelectedFile(new File(pn.isEmpty() ? "订单明细.xlsx" : pn + "-订单明细.xlsx"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".xlsx")) file = new File(file.getAbsolutePath() + ".xlsx");
            try {
                bomService.exportExcel(currentRows, file, pn);
                JOptionPane.showMessageDialog(this, "导出成功: " + file.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage());
            }
        }
    }

    private String printTitle() {
        String pn = projectNameField.getText().trim();
        return pn.isEmpty() ? "订单明细清单" : pn + " - 订单明细清单";
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
