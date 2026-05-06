package com.bom.ui;

import com.bom.model.Component;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 可搜索的下拉选择面板：搜索框 + 过滤列表
 */
public class SearchableDropdownPanel extends JPanel {
    private final JTextField searchField;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<Component> allItems;

    public SearchableDropdownPanel(List<Component> items) {
        this.allItems = items != null ? items : new ArrayList<>();
        setLayout(new BorderLayout(5, 5));

        searchField = new JTextField();
        searchField.setToolTipText("输入编号或名称进行搜索");
        add(searchField, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"编号", "名称", "规格"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 搜索过滤
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });

        // 双击选中
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // 由外部处理
                }
            }
        });

        filter();
    }

    public void setItems(List<Component> items) {
        this.allItems = items != null ? items : new ArrayList<>();
        filter();
    }

    private void filter() {
        String keyword = searchField.getText().trim().toLowerCase();
        tableModel.setRowCount(0);
        for (Component c : allItems) {
            if (keyword.isEmpty() ||
                (c.getCode() != null && c.getCode().toLowerCase().contains(keyword)) ||
                (c.getName() != null && c.getName().toLowerCase().contains(keyword)) ||
                (c.getSpec() != null && c.getSpec().toLowerCase().contains(keyword))) {
                tableModel.addRow(new Object[]{c.getCode(), c.getName(), c.getSpec()});
            }
        }
        if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    public Component getSelectedComponent() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        String code = (String) tableModel.getValueAt(row, 0);
        for (Component c : allItems) {
            if (c.getCode() != null && c.getCode().equals(code)) {
                return c;
            }
        }
        return null;
    }

    public JTextField getSearchField() {
        return searchField;
    }
}
