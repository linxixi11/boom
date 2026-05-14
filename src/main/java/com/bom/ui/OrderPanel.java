package com.bom.ui;

import com.bom.service.BomService;
import com.bom.service.BomService.BomSummaryRow;
import com.bom.service.OrderService;
import com.bom.service.OrderService.Order;
import com.bom.service.OrderService.OrderItem;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.print.PageFormat;
import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class OrderPanel extends JPanel {
    private final OrderService orderService = new OrderService();
    private final BomService bomService = new BomService();
    private final DefaultTableModel orderModel;
    private final JTable orderTable;
    private final DefaultTableModel itemModel;
    private final JTable itemTable;
    private final TableRowSorter<DefaultTableModel> itemSorter;
    
    private final JTextField searchField;
    private final JComboBox<String> typeFilter;
    private final JLabel itemCountLabel;
    
    private List<Order> allOrders = new ArrayList<>();
    private List<OrderItem> currentItems = new ArrayList<>();
    private PageFormat pageFormat = TablePrintSupport.defaultLandscapeA4();
    private String currentProjectName = "";

    public OrderPanel() {
        setLayout(new BorderLayout());
        setBackground(UIStyle.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // ====== 左侧订单列表 ======
        JPanel leftPanel = UIStyle.section();
        JPanel leftHeader = new JPanel(new BorderLayout());
        leftHeader.setOpaque(false);
        leftHeader.add(UIStyle.sectionLabel("历史订单"), BorderLayout.WEST);
        leftPanel.add(leftHeader, BorderLayout.NORTH);

        orderModel = new DefaultTableModel(new String[]{"ID", "项目名称", "创建时间"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        orderTable = UIStyle.createTable(orderModel);
        UIStyle.hideColumn(orderTable, 0);
        orderTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        orderTable.getColumnModel().getColumn(2).setMaxWidth(150);
        
        leftPanel.add(UIStyle.wrap(orderTable), BorderLayout.CENTER);

        JButton newOrderBtn = UIStyle.primaryButton("新建订单");
        JButton deleteBtn = UIStyle.button("删除订单");
        leftPanel.add(UIStyle.buttonRow(newOrderBtn, deleteBtn), BorderLayout.SOUTH);

        // ====== 右侧订单明细 ======
        JPanel rightPanel = UIStyle.section();
        JPanel rightHeader = new JPanel(new BorderLayout(6, 0));
        rightHeader.setOpaque(false);
        rightHeader.add(UIStyle.sectionLabel("订单明细"), BorderLayout.WEST);
        
        itemCountLabel = UIStyle.hintLabel("共 0 行");
        rightHeader.add(itemCountLabel, BorderLayout.EAST);
        rightPanel.add(rightHeader, BorderLayout.NORTH);

        itemModel = new DefaultTableModel(
            new String[]{"序号", "类型", "物料编号", "物料名称", "规格型号", "材质", "单位", "总需求", "库存数量", "扣库存", "需补数量", "库存备注"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        itemTable = UIStyle.createTable(itemModel);
        itemSorter = new TableRowSorter<>(itemModel);
        itemTable.setRowSorter(itemSorter);
        itemTable.getColumnModel().getColumn(0).setMaxWidth(52);
        itemTable.getColumnModel().getColumn(1).setMaxWidth(70);
        itemTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        itemTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        JPanel rightCenter = new JPanel(new BorderLayout(6, 6));
        rightCenter.setOpaque(false);
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterPanel.setOpaque(false);
        typeFilter = new JComboBox<>(new String[]{"全部", "成品", "半成品", "零件", "外购件"});
        typeFilter.setFont(UIStyle.FONT);
        searchField = new JTextField(16);
        searchField.setFont(UIStyle.FONT);
        searchField.setToolTipText("筛选结果中的编号 / 名称 / 规格 / 材质");
        filterPanel.add(new JLabel("筛选类型"));
        filterPanel.add(typeFilter);
        filterPanel.add(new JLabel("关键字"));
        filterPanel.add(searchField);
        rightCenter.add(filterPanel, BorderLayout.NORTH);
        rightCenter.add(UIStyle.wrap(itemTable), BorderLayout.CENTER);
        rightPanel.add(rightCenter, BorderLayout.CENTER);

        JButton pageSetupBtn = UIStyle.button("页面设置");
        JButton previewBtn = UIStyle.button("打印预览");
        JButton exportExcelBtn = UIStyle.button("导出 Excel");
        JButton printBtn = UIStyle.primaryButton("打印此订单");
        rightPanel.add(UIStyle.buttonRowRight(pageSetupBtn, previewBtn, exportExcelBtn, printBtn), BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setResizeWeight(0.3);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        mainSplit.setOpaque(false);
        UIStyle.rememberDividerLocation(mainSplit, "order.split", 350);
        add(mainSplit, BorderLayout.CENTER);

        // ====== 事件 ======
        orderTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedOrderDetails();
            }
        });

        newOrderBtn.addActionListener(e -> OrderDraftDialog.show(this, this::refreshData));
        deleteBtn.addActionListener(e -> deleteSelectedOrder());

        DocumentListener filterListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        };
        searchField.getDocument().addDocumentListener(filterListener);
        typeFilter.addActionListener(e -> applyFilter());

        pageSetupBtn.addActionListener(e -> pageFormat = TablePrintSupport.showPageSetup(this, pageFormat));
        previewBtn.addActionListener(e -> TablePrintSupport.showPreview(this, itemTable, pageFormat, printTitle()));
        exportExcelBtn.addActionListener(e -> exportCurrentOrderExcel());
        printBtn.addActionListener(e -> TablePrintSupport.print(this, itemTable, pageFormat, printTitle()));

        refreshData();
    }

    public void refreshData() {
        try {
            allOrders = orderService.getAllOrders();
            orderModel.setRowCount(0);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (Order order : allOrders) {
                orderModel.addRow(new Object[]{
                    order.id, order.projectName, sdf.format(order.createdAt)
                });
            }
            if (orderModel.getRowCount() > 0) {
                orderTable.setRowSelectionInterval(0, 0);
            } else {
                clearItemDetails();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载订单失败: " + e.getMessage());
        }
    }

    private void loadSelectedOrderDetails() {
        int row = orderTable.getSelectedRow();
        if (row < 0) {
            clearItemDetails();
            return;
        }
        Long orderId = (Long) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 0);
        currentProjectName = (String) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 1);
        try {
            currentItems = orderService.getOrderItems(orderId);
            itemModel.setRowCount(0);
            for (OrderItem item : currentItems) {
                itemModel.addRow(new Object[]{
                    item.seqNo, item.componentType, item.componentCode, item.componentName,
                    item.spec, item.material, item.unit, formatQty(item.totalQty),
                    formatQty(item.stockQty), formatQty(item.deductedQty),
                    formatQty(item.shortageQty), item.stockRemark
                });
            }
            applyFilter();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载订单明细失败: " + e.getMessage());
        }
    }

    private void clearItemDetails() {
        currentProjectName = "";
        currentItems.clear();
        itemModel.setRowCount(0);
        updateItemCount();
    }

    private void deleteSelectedOrder() {
        int row = orderTable.getSelectedRow();
        if (row < 0) return;
        Long orderId = (Long) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 0);
        String pName = (String) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 1);
        
        int confirm = JOptionPane.showConfirmDialog(this, 
                "确认删除订单 [" + pName + "] 吗？", "删除订单", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                orderService.deleteOrder(orderId);
                refreshData();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "删除失败: " + e.getMessage());
            }
        }
    }

    private void applyFilter() {
        String type = (String) typeFilter.getSelectedItem();
        String keyword = searchField.getText().trim().toLowerCase();
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
        itemSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        
        // 动态更新序号（为了打印时显示连续的序号）
        for (int i = 0; i < itemTable.getRowCount(); i++) {
            int modelRow = itemTable.convertRowIndexToModel(i);
            itemModel.setValueAt(i + 1, modelRow, 0);
        }
        updateItemCount();
    }

    private void updateItemCount() {
        itemCountLabel.setText("显示 " + itemTable.getRowCount() + " / 共 " + itemModel.getRowCount() + " 行");
    }

    private String printTitle() {
        return currentProjectName.isEmpty() ? "订单明细清单" : currentProjectName + " - 订单明细清单";
    }

    private void exportCurrentOrderExcel() {
        if (currentItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可导出的订单明细");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件", "xlsx"));
        String fileName = currentProjectName == null || currentProjectName.trim().isEmpty() ? "订单明细.xlsx" : currentProjectName + "-订单明细.xlsx";
        chooser.setSelectedFile(new File(fileName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".xlsx")) file = new File(file.getAbsolutePath() + ".xlsx");
            try {
                bomService.exportExcel(toSummaryRows(currentItems), file, currentProjectName);
                JOptionPane.showMessageDialog(this, "导出成功: " + file.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage());
            }
        }
    }

    private List<BomSummaryRow> toSummaryRows(List<OrderItem> items) {
        List<BomSummaryRow> rows = new ArrayList<>();
        int seq = 1;
        for (OrderItem item : items) {
            rows.add(new BomSummaryRow(seq++, null, item.componentType, "", item.componentCode,
                item.componentName, item.spec, item.material, item.unit, item.totalQty,
                item.stockQty, item.deductedQty, item.shortageQty, item.stockRemark));
        }
        return rows;
    }

    private String formatQty(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) {
            return String.valueOf((long) qty);
        }
        return String.valueOf(qty);
    }
}
