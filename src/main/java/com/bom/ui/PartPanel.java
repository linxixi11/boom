package com.bom.ui;

import com.bom.dao.ComponentDao;
import com.bom.model.Component;
import com.bom.service.OptionService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartPanel extends JPanel {
    private static final Map<String, String> lastSavedCodeByType = new HashMap<>();

    private final ComponentDao componentDao = new ComponentDao();
    private final String componentType;
    private final String libraryTitle;
    private final String itemLabel;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public PartPanel() {
        this(Component.TYPE_PART, "零件库", "零件");
    }

    public PartPanel(String componentType, String libraryTitle, String itemLabel) {
        this.componentType = componentType;
        this.libraryTitle = libraryTitle;
        this.itemLabel = itemLabel;

        setLayout(new BorderLayout(0, 8));
        setBackground(UIStyle.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIStyle.sectionLabel(libraryTitle), BorderLayout.WEST);

        JButton addBtn = UIStyle.primaryButton("新增");
        JButton editBtn = UIStyle.button("编辑");
        JButton copyBtn = UIStyle.button("复制");
        JButton delBtn = UIStyle.dangerButton("删除");
        JButton exportBtn = UIStyle.button("导出清单");
        JButton importBtn = UIStyle.button("导入清单");
        JButton refreshBtn = UIStyle.button("刷新");
        header.add(UIStyle.buttonRowRight(addBtn, editBtn, copyBtn, delBtn, exportBtn, importBtn, refreshBtn), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
            new String[]{"ID", itemLabel + "编号", "名称", "规格型号", "单位", "材质", "库存数量", "备注"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        table = UIStyle.createTable(tableModel);
        UIStyle.hideColumn(table, 0);
        add(UIStyle.wrap(table), BorderLayout.CENTER);

        addBtn.addActionListener(e -> showEditDialog(null, false));
        editBtn.addActionListener(e -> withSelected(c -> showEditDialog(c, false)));
        copyBtn.addActionListener(e -> withSelected(c -> showEditDialog(c, true)));
        delBtn.addActionListener(e -> deleteSelected());
        exportBtn.addActionListener(e -> ComponentSheetActions.exportAll(this));
        importBtn.addActionListener(e -> {
            if (ComponentSheetActions.importAll(this)) refreshData();
        });
        refreshBtn.addActionListener(e -> refreshData());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) withSelected(c -> showEditDialog(c, false));
            }
        });

        refreshData();
    }

    public void refreshData() {
        try {
            tableModel.setRowCount(0);
            List<Component> parts = componentDao.findByType(componentType);
            for (Component c : parts) {
                tableModel.addRow(new Object[]{c.getId(), c.getCode(), c.getName(),
                    c.getSpec(), c.getUnit(), c.getMaterial(), formatQty(c.getStockQty()), c.getRemark()});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载失败: " + e.getMessage());
        }
    }

    private interface ComponentCallback { void run(Component c); }

    private void withSelected(ComponentCallback cb) {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "请先选择一行"); return; }
        Long id = (Long) tableModel.getValueAt(table.convertRowIndexToModel(row), 0);
        try {
            Component c = componentDao.findById(id);
            if (c != null) cb.run(c);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "查询失败: " + ex.getMessage());
        }
    }

    private void deleteSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) { JOptionPane.showMessageDialog(this, "请先选择要删除的" + itemLabel); return; }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int row : rows) {
            ids.add((Long) tableModel.getValueAt(table.convertRowIndexToModel(row), 0));
        }
        String message = rows.length == 1 ? "确认删除该" + itemLabel + "？"
                : "确认删除选中的 " + rows.length + " 个" + itemLabel + "？相关 BOM 引用也会移除。";
        if (JOptionPane.showConfirmDialog(this, message, "确认", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try {
                componentDao.deleteMany(ids);
                refreshData();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "删除失败: " + ex.getMessage());
            }
        }
    }

    private void showEditDialog(Component source, boolean copyMode) {
        boolean isNew = (source == null) || copyMode;
        String title = source == null ? "新增" + itemLabel : (copyMode ? "复制" + itemLabel : "编辑" + itemLabel);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), title, true);
        dialog.setLayout(new BorderLayout(0, 0));
        dialog.getContentPane().setBackground(UIStyle.BG);
        dialog.setSize(500, 420);
        dialog.setLocationRelativeTo(this);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        String defaultCode = "";
        if (isNew) {
            try { defaultCode = suggestNextCode(); } catch (SQLException ignored) {}
        }
        JTextField codeField = new JTextField(source == null ? defaultCode : (copyMode ? defaultCode : source.getCode()));
        JTextField nameField = new JTextField(source == null ? "" :
            (copyMode ? (safe(source.getName()) + " (副本)") : safe(source.getName())));
        JTextField specField = new JTextField(source == null ? "" : safe(source.getSpec()));
        OptionComboBox unitField = new OptionComboBox(OptionService.CATEGORY_UNIT, source == null ? "" : safe(source.getUnit()));
        OptionComboBox materialField = new OptionComboBox(OptionService.CATEGORY_MATERIAL, source == null ? "" : safe(source.getMaterial()));
        JTextField stockField = new JTextField(source == null ? "0" : formatQty(source.getStockQty()));
        JTextField remarkField = new JTextField(source == null ? "" : safe(source.getRemark()));

        addFormRow(form, g, 0, itemLabel + "编号", codeField);
        addFormRow(form, g, 1, "名称", nameField);
        addFormRow(form, g, 2, "规格型号", specField);
        addFormRow(form, g, 3, "单位", unitField);
        addFormRow(form, g, 4, "材质", materialField);
        addFormRow(form, g, 5, "库存数量", stockField);
        addFormRow(form, g, 6, "备注", remarkField);
        dialog.add(form, BorderLayout.CENTER);

        JButton saveBtn = UIStyle.primaryButton("保存");
        JButton saveContinueBtn = UIStyle.button("保存并继续");
        JButton cancelBtn = UIStyle.button("取消");
        JPanel btnPanel = UIStyle.buttonRowRight(saveContinueBtn, saveBtn, cancelBtn);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 18, 14, 18));
        dialog.add(btnPanel, BorderLayout.SOUTH);

        Runnable saveOnce = () -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { JOptionPane.showMessageDialog(dialog, "名称不能为空"); return; }
            double stockQty;
            try {
                stockQty = parseNonNegativeQty(stockField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效库存数量"); return;
            }
            try {
                Component c = isNew ? new Component() : source;
                c.setType(componentType);
                c.setCode(codeField.getText().trim());
                c.setName(name);
                c.setSpec(specField.getText().trim());
                c.setUnit(unitField.getText().trim());
                c.setMaterial(materialField.getText().trim());
                c.setStockQty(stockQty);
                c.setRemark(remarkField.getText().trim());
                if (isNew) componentDao.insert(c); else componentDao.update(c);
                rememberCode(c.getCode());
                dialog.dispose();
                refreshData();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "保存失败: " + ex.getMessage());
            }
        };
        saveBtn.addActionListener(e -> saveOnce.run());
        saveContinueBtn.addActionListener(e -> {
            if (!isNew) {
                saveOnce.run();
                return;
            }
            String name = nameField.getText().trim();
            if (name.isEmpty()) { JOptionPane.showMessageDialog(dialog, "名称不能为空"); return; }
            double stockQty;
            try {
                stockQty = parseNonNegativeQty(stockField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效库存数量"); return;
            }
            try {
                Component c = new Component();
                c.setType(componentType);
                c.setCode(codeField.getText().trim());
                c.setName(name);
                c.setSpec(specField.getText().trim());
                c.setUnit(unitField.getText().trim());
                c.setMaterial(materialField.getText().trim());
                c.setStockQty(stockQty);
                c.setRemark(remarkField.getText().trim());
                componentDao.insert(c);
                rememberCode(c.getCode());
                codeField.setText(incrementCode(c.getCode()));
                nameField.setText("");
                specField.setText("");
                unitField.setText("");
                materialField.setText("");
                stockField.setText("0");
                remarkField.setText("");
                nameField.requestFocusInWindow();
                refreshData();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "保存失败: " + ex.getMessage());
            }
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(saveBtn);
        dialog.setVisible(true);
    }

    private String suggestNextCode() throws SQLException {
        String lastCode = lastSavedCodeByType.get(componentType);
        if (lastCode != null && !lastCode.trim().isEmpty()) {
            return incrementCode(lastCode);
        }
        return componentDao.nextCode(componentType);
    }

    private void rememberCode(String code) {
        if (code != null && !code.trim().isEmpty()) {
            lastSavedCodeByType.put(componentType, code.trim());
        }
    }

    private static String incrementCode(String code) {
        if (code == null || code.trim().isEmpty()) return "";
        Matcher matcher = Pattern.compile("(\\d+)(?!.*\\d)").matcher(code.trim());
        if (!matcher.find()) return code.trim() + "-001";
        String numberText = matcher.group(1);
        long next = Long.parseLong(numberText) + 1;
        String nextText = String.format("%0" + numberText.length() + "d", next);
        return code.trim().substring(0, matcher.start(1)) + nextText + code.trim().substring(matcher.end(1));
    }

    static void addFormRow(JPanel form, GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(UIStyle.TEXT_MUTED);
        l.setPreferredSize(new Dimension(80, 28));
        form.add(l, g);
        g.gridx = 1; g.weightx = 1;
        form.add(field, g);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    static double parseNonNegativeQty(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        double qty = Double.parseDouble(text.trim());
        if (qty < 0) throw new NumberFormatException();
        return qty;
    }

    static String formatQty(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) {
            return String.valueOf((long) qty);
        }
        return String.valueOf(qty);
    }
}
