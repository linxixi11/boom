package com.bom.ui;

import com.bom.dao.BomItemDao;
import com.bom.dao.ComponentDao;
import com.bom.model.BomItem;
import com.bom.model.Component;
import com.bom.service.OptionService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductPanel extends JPanel {
    private final ComponentDao componentDao = new ComponentDao();
    private final BomItemDao bomItemDao = new BomItemDao();
    private final DefaultTableModel listModel;
    private final DefaultTableModel bomModel;
    private final JTable listTable;
    private final JTable bomTable;

    public ProductPanel() {
        setLayout(new BorderLayout());
        setBackground(UIStyle.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // ---- 左侧：成品列表 ----
        JPanel leftPanel = UIStyle.section();
        JPanel leftHeader = new JPanel(new BorderLayout());
        leftHeader.setOpaque(false);
        leftHeader.add(UIStyle.sectionLabel("成品"), BorderLayout.WEST);

        JButton addBtn = UIStyle.primaryButton("新增");
        JButton editBtn = UIStyle.button("编辑");
        JButton copyBtn = UIStyle.button("复制");
        JButton delBtn = UIStyle.dangerButton("删除");
        JButton moveToSemiBtn = UIStyle.button("转为半成品");
        JButton exportBtn = UIStyle.button("导出清单");
        JButton importBtn = UIStyle.button("导入清单");
        leftHeader.add(UIStyle.buttonRowRight(addBtn, editBtn, copyBtn, delBtn, moveToSemiBtn, exportBtn, importBtn), BorderLayout.EAST);
        leftPanel.add(leftHeader, BorderLayout.NORTH);

        listModel = new DefaultTableModel(new String[]{"ID", "编号", "名称", "规格", "单位", "材质"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        listTable = UIStyle.createTable(listModel);
        UIStyle.hideColumn(listTable, 0);
        leftPanel.add(UIStyle.wrap(listTable), BorderLayout.CENTER);

        // ---- 右侧：BOM 子件 ----
        JPanel rightPanel = UIStyle.section();
        JPanel rightHeader = new JPanel(new BorderLayout());
        rightHeader.setOpaque(false);
        rightHeader.add(UIStyle.sectionLabel("组成子件"), BorderLayout.WEST);

        JButton addItemBtn = UIStyle.button("添加子件");
        JButton editItemBtn = UIStyle.button("修改用量");
        JButton copyChildBtn = UIStyle.button("复制子件");
        JButton pasteChildBtn = UIStyle.button("粘贴子件");
        JButton delItemBtn = UIStyle.dangerButton("移除");
        rightHeader.add(UIStyle.buttonRowRight(addItemBtn, editItemBtn, copyChildBtn, pasteChildBtn, delItemBtn), BorderLayout.EAST);
        rightPanel.add(rightHeader, BorderLayout.NORTH);

        bomModel = new DefaultTableModel(new String[]{"ID", "子件ID", "子件编号", "子件名称", "类型", "规格", "单位", "用量"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 || c == 1 ? Long.class : String.class; }
        };
        bomTable = UIStyle.createTable(bomModel);
        UIStyle.hideColumn(bomTable, 0);
        UIStyle.hideColumn(bomTable, 1);
        rightPanel.add(UIStyle.wrap(bomTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setResizeWeight(0.42);
        split.setBorder(null);
        split.setDividerSize(8);
        split.setOpaque(false);
        UIStyle.rememberDividerLocation(split, "product.split", 420);
        add(split, BorderLayout.CENTER);

        listTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadBomItems();
        });
        listTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        addBtn.addActionListener(e -> showEditDialog(null, false));
        editBtn.addActionListener(e -> editSelected());
        copyBtn.addActionListener(e -> copySelected());
        delBtn.addActionListener(e -> deleteSelected());
        moveToSemiBtn.addActionListener(e -> moveSelectedTo(Component.TYPE_SEMI, "半成品"));
        exportBtn.addActionListener(e -> ComponentSheetActions.exportAll(this));
        importBtn.addActionListener(e -> {
            if (ComponentSheetActions.importAll(this)) refreshData();
        });

        addItemBtn.addActionListener(e -> {
            Long pid = selectedListId();
            if (pid == null) { JOptionPane.showMessageDialog(this, "请先选择成品"); return; }
            showAddChildDialog(pid);
        });
        editItemBtn.addActionListener(e -> {
            Long bomId = selectedBomId();
            if (bomId == null) { JOptionPane.showMessageDialog(this, "请先选择子件"); return; }
            showEditQuantityDialog(bomId);
        });
        delItemBtn.addActionListener(e -> {
            java.util.List<Long> bomIds = selectedBomIds();
            if (bomIds.isEmpty()) { JOptionPane.showMessageDialog(this, "请先选择子件"); return; }
            try {
                for (Long bomId : bomIds) bomItemDao.delete(bomId);
                loadBomItems();
            }
            catch (SQLException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        });

        // 复制选中的子件引用到剪贴板
        copyChildBtn.addActionListener(e -> {
            java.util.List<Long> bomIds = selectedBomIds();
            if (bomIds.isEmpty()) { JOptionPane.showMessageDialog(this, "请先选择子件"); return; }
            java.util.List<BomChildClipboard.ChildRef> refs = new ArrayList<>();
            for (int viewRow : bomTable.getSelectedRows()) {
                int mr = bomTable.convertRowIndexToModel(viewRow);
                Long childId = (Long) bomModel.getValueAt(mr, 1);
                String code = (String) bomModel.getValueAt(mr, 2);
                String name = (String) bomModel.getValueAt(mr, 3);
                String type = (String) bomModel.getValueAt(mr, 4);
                String spec = (String) bomModel.getValueAt(mr, 5);
                String unit = (String) bomModel.getValueAt(mr, 6);
                double qty = parseQty(bomModel.getValueAt(mr, 7));
                refs.add(new BomChildClipboard.ChildRef(childId, type, code, name, spec, unit, qty));
            }
            BomChildClipboard.copy(refs);
            JOptionPane.showMessageDialog(this, "已复制 " + refs.size() + " 个子件引用");
        });

        // 粘贴剪贴板中的子件引用到当前成品
        pasteChildBtn.addActionListener(e -> {
            Long pid = selectedListId();
            if (pid == null) { JOptionPane.showMessageDialog(this, "请先选择成品"); return; }
            if (BomChildClipboard.isEmpty()) { JOptionPane.showMessageDialog(this, "剪贴板为空，请先复制子件"); return; }
            java.util.List<BomChildClipboard.ChildRef> refs = BomChildClipboard.paste();
            int added = 0;
            try {
                // 获取当前成品已有的子件ID
                java.util.Set<Long> existingChildIds = new java.util.HashSet<>();
                for (BomItem item : bomItemDao.findByParentId(pid)) {
                    existingChildIds.add(item.getChildId());
                }
                for (BomChildClipboard.ChildRef ref : refs) {
                    if (!existingChildIds.contains(ref.childId)) {
                        bomItemDao.insert(new BomItem(pid, ref.childId, ref.quantity));
                        existingChildIds.add(ref.childId);
                        added++;
                    }
                }
                loadBomItems();
                JOptionPane.showMessageDialog(this, "已粘贴 " + added + " 个子件（跳过已存在的）");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "粘贴失败: " + ex.getMessage());
            }
        });

        refreshData();
    }

    private Long selectedListId() {
        int row = listTable.getSelectedRow();
        if (row < 0) return null;
        return (Long) listModel.getValueAt(listTable.convertRowIndexToModel(row), 0);
    }

    private Long selectedBomId() {
        int row = bomTable.getSelectedRow();
        if (row < 0) return null;
        return (Long) bomModel.getValueAt(bomTable.convertRowIndexToModel(row), 0);
    }

    private java.util.List<Long> selectedListIds() {
        java.util.List<Long> ids = new ArrayList<>();
        for (int row : listTable.getSelectedRows()) {
            ids.add((Long) listModel.getValueAt(listTable.convertRowIndexToModel(row), 0));
        }
        return ids;
    }

    private java.util.List<Long> selectedBomIds() {
        java.util.List<Long> ids = new ArrayList<>();
        for (int row : bomTable.getSelectedRows()) {
            ids.add((Long) bomModel.getValueAt(bomTable.convertRowIndexToModel(row), 0));
        }
        return ids;
    }

    private void editSelected() {
        Long id = selectedListId();
        if (id == null) { JOptionPane.showMessageDialog(this, "请先选择成品"); return; }
        try {
            Component c = componentDao.findById(id);
            if (c != null) showEditDialog(c, false);
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private void copySelected() {
        Long id = selectedListId();
        if (id == null) { JOptionPane.showMessageDialog(this, "请先选择成品"); return; }
        try {
            Component c = componentDao.findById(id);
            if (c != null) showEditDialog(c, true);
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private void deleteSelected() {
        java.util.List<Long> ids = selectedListIds();
        if (ids.isEmpty()) { JOptionPane.showMessageDialog(this, "请先选择成品"); return; }
        String message = ids.size() == 1 ? "确认删除该成品？" : "确认删除选中的 " + ids.size() + " 个成品？相关 BOM 引用也会移除。";
        if (JOptionPane.showConfirmDialog(this, message, "确认", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try { componentDao.deleteMany(ids); refreshData(); }
            catch (SQLException ex) { JOptionPane.showMessageDialog(this, "删除失败: " + ex.getMessage()); }
        }
    }

    private void moveSelectedTo(String targetType, String targetLabel) {
        java.util.List<Long> ids = selectedListIds();
        if (ids.isEmpty()) { JOptionPane.showMessageDialog(this, "请先选择成品"); return; }
        if (JOptionPane.showConfirmDialog(this, "确认将选中的 " + ids.size() + " 个成品转为" + targetLabel + "？",
                "确认", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            for (Long id : ids) componentDao.updateType(id, targetType);
            refreshData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "移动失败: " + ex.getMessage());
        }
    }

    public void refreshData() {
        try {
            listModel.setRowCount(0);
            for (Component c : componentDao.findByType(Component.TYPE_PRODUCT)) {
                listModel.addRow(new Object[]{c.getId(), c.getCode(), c.getName(), c.getSpec(), c.getUnit(), c.getMaterial()});
            }
            bomModel.setRowCount(0);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private void loadBomItems() {
        loadBomItems(java.util.Collections.emptySet());
    }

    private void loadBomItems(java.util.Set<Long> highlightChildIds) {
        Long pid = selectedListId();
        if (pid == null) return;
        try {
            bomModel.setRowCount(0);
            for (BomItem item : bomItemDao.findByParentId(pid)) {
                bomModel.addRow(new Object[]{item.getId(), item.getChildId(), item.getChildCode(), item.getChildName(),
                    typeLabel(item.getChildType()), item.getChildSpec(), item.getChildUnit(), item.getQuantity()});
            }
            highlightBomChildren(highlightChildIds);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private void highlightBomChildren(java.util.Set<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) return;
        bomTable.clearSelection();
        for (int i = 0; i < bomModel.getRowCount(); i++) {
            Long childId = (Long) bomModel.getValueAt(i, 1);
            if (childIds.contains(childId)) {
                int viewRow = bomTable.convertRowIndexToView(i);
                bomTable.addRowSelectionInterval(viewRow, viewRow);
                bomTable.scrollRectToVisible(bomTable.getCellRect(viewRow, 0, true));
            }
        }
    }

    private static String typeLabel(String t) {
        if (Component.TYPE_PART.equals(t)) return "零件";
        if (Component.TYPE_PURCHASE.equals(t)) return "外购件";
        if (Component.TYPE_SEMI.equals(t)) return "半成品";
        if (Component.TYPE_PRODUCT.equals(t)) return "成品";
        return t;
    }

    private List<Component> loadProductChildCandidates() throws SQLException {
        List<Component> candidates = new ArrayList<>();
        candidates.addAll(componentDao.findByType(Component.TYPE_SEMI));
        candidates.addAll(componentDao.findByType(Component.TYPE_PART));
        candidates.addAll(componentDao.findByType(Component.TYPE_PURCHASE));
        return candidates;
    }

    private void showEditDialog(Component source, boolean copyMode) {
        boolean isNew = (source == null) || copyMode;
        String title = source == null ? "新增成品" : (copyMode ? "复制成品" : "编辑成品");
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), title, true);
        dialog.setLayout(new BorderLayout(0, 0));
        dialog.getContentPane().setBackground(UIStyle.BG);
        UIStyle.rememberWindowBounds(dialog, "dialog.product.edit.bounds", new Dimension(620, 540), this);

        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setOpaque(false);
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("基本信息"),
            BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        String defaultCode = "";
        if (isNew) {
            try { defaultCode = componentDao.nextCode(Component.TYPE_PRODUCT); } catch (SQLException ignored) {}
        }
        JTextField codeField = new JTextField(source == null ? defaultCode : (copyMode ? defaultCode : safe(source.getCode())));
        JTextField nameField = new JTextField(source == null ? "" :
            (copyMode ? (safe(source.getName()) + " (副本)") : safe(source.getName())));
        JTextField specField = new JTextField(source == null ? "" : safe(source.getSpec()));
        OptionComboBox unitField = new OptionComboBox(OptionService.CATEGORY_UNIT, source == null ? "" : safe(source.getUnit()));
        OptionComboBox materialField = new OptionComboBox(OptionService.CATEGORY_MATERIAL, source == null ? "" : safe(source.getMaterial()));
        JTextField remarkField = new JTextField(source == null ? "" : safe(source.getRemark()));
        PartPanel.addFormRow(infoPanel, g, 0, "编号", codeField);
        PartPanel.addFormRow(infoPanel, g, 1, "名称", nameField);
        PartPanel.addFormRow(infoPanel, g, 2, "规格", specField);
        PartPanel.addFormRow(infoPanel, g, 3, "单位", unitField);
        PartPanel.addFormRow(infoPanel, g, 4, "材质", materialField);
        PartPanel.addFormRow(infoPanel, g, 5, "备注", remarkField);
        dialog.add(infoPanel, BorderLayout.NORTH);

        // 子件
        JPanel bomPanel = new JPanel(new BorderLayout(6, 6));
        bomPanel.setOpaque(false);
        bomPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("子件配置"),
            BorderFactory.createEmptyBorder(4, 8, 8, 8)));

        JPanel addRow = new JPanel(new BorderLayout(6, 6));
        addRow.setOpaque(false);
        SearchableComboBox searchCombo = new SearchableComboBox(new ArrayList<>());
        addRow.add(searchCombo, BorderLayout.CENTER);

        JPanel addBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addBtns.setOpaque(false);
        JTextField qtyField = new JTextField("1", 5);
        JButton addChildBtn = UIStyle.button("添加");
        addBtns.add(new JLabel("用量"));
        addBtns.add(qtyField);
        addBtns.add(addChildBtn);
        addRow.add(addBtns, BorderLayout.EAST);
        bomPanel.add(addRow, BorderLayout.NORTH);

        DefaultTableModel childModel = new DefaultTableModel(new String[]{"子件ID", "类型", "编号", "名称", "规格", "单位", "用量"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        JTable childTable = UIStyle.createTable(childModel);
        UIStyle.hideColumn(childTable, 0);
        JScrollPane childScroll = UIStyle.wrap(childTable);
        childScroll.setPreferredSize(new Dimension(0, 180));
        bomPanel.add(childScroll, BorderLayout.CENTER);

        JButton delChildBtn = UIStyle.dangerButton("移除选中");
        bomPanel.add(UIStyle.buttonRow(delChildBtn), BorderLayout.SOUTH);
        dialog.add(bomPanel, BorderLayout.CENTER);

        try {
            searchCombo.setItems(loadProductChildCandidates());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage());
        }

        if (source != null && source.getId() != null) {
            try {
                for (BomItem item : bomItemDao.findByParentId(source.getId())) {
                    childModel.addRow(new Object[]{item.getChildId(), typeLabel(item.getChildType()), item.getChildCode(),
                        item.getChildName(), item.getChildSpec(), item.getChildUnit(), item.getQuantity()});
                }
            } catch (SQLException ignored) {}
        }

        addChildBtn.addActionListener(e -> {
            Component sel = searchCombo.getSelected();
            if (sel == null) { JOptionPane.showMessageDialog(dialog, "请搜索并选择子件"); return; }
            double qty;
            try {
                qty = Double.parseDouble(qtyField.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效数量"); return;
            }
            for (int i = 0; i < childModel.getRowCount(); i++) {
                if (sel.getId().equals(childModel.getValueAt(i, 0))) {
                    JOptionPane.showMessageDialog(dialog, "该子件已添加"); return;
                }
            }
            childModel.addRow(new Object[]{sel.getId(), typeLabel(sel.getType()), sel.getCode(),
                sel.getName(), sel.getSpec(), sel.getUnit(), qty});
            int newRow = childModel.getRowCount() - 1;
            int viewRow = childTable.convertRowIndexToView(newRow);
            childTable.setRowSelectionInterval(viewRow, viewRow);
            childTable.scrollRectToVisible(childTable.getCellRect(viewRow, 0, true));
            searchCombo.clearSelection();
            qtyField.setText("1");
        });
        delChildBtn.addActionListener(e -> {
            int row = childTable.getSelectedRow();
            if (row >= 0) childModel.removeRow(childTable.convertRowIndexToModel(row));
        });

        JButton saveBtn = UIStyle.primaryButton("保存");
        JButton cancelBtn = UIStyle.button("取消");
        JPanel btnPanel = UIStyle.buttonRowRight(saveBtn, cancelBtn);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 10, 8));
        dialog.add(btnPanel, BorderLayout.SOUTH);

        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { JOptionPane.showMessageDialog(dialog, "名称不能为空"); return; }
            try {
                Component c = isNew ? new Component() : source;
                c.setType(Component.TYPE_PRODUCT);
                c.setCode(codeField.getText().trim());
                c.setName(name);
                c.setSpec(specField.getText().trim());
                c.setUnit(unitField.getText().trim());
                c.setMaterial(materialField.getText().trim());
                c.setRemark(remarkField.getText().trim());
                if (isNew) {
                    componentDao.insert(c);
                } else {
                    componentDao.update(c);
                    bomItemDao.deleteByParentId(c.getId());
                }
                for (int i = 0; i < childModel.getRowCount(); i++) {
                    Long childId = (Long) childModel.getValueAt(i, 0);
                    double qty = parseQty(childModel.getValueAt(i, 6));
                    bomItemDao.insert(new BomItem(c.getId(), childId, qty));
                }
                dialog.dispose();
                refreshData();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage());
            }
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(saveBtn);
        dialog.setVisible(true);
    }

    private double parseQty(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 1; }
    }

    private void showAddChildDialog(Long productId) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "添加子件", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.getContentPane().setBackground(UIStyle.BG);
        UIStyle.rememberWindowBounds(dialog, "dialog.product.addChild.bounds", new Dimension(540, 360), this);

        List<Component> candidates;
        try { candidates = loadProductChildCandidates(); }
        catch (SQLException ex) {
            candidates = new ArrayList<>();
            JOptionPane.showMessageDialog(dialog, ex.getMessage());
        }
        SearchableComboBox searchCombo = new SearchableComboBox(candidates);
        JPanel topPanel = new JPanel(new BorderLayout(6, 0));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        topPanel.add(new JLabel("搜索子件"), BorderLayout.WEST);
        topPanel.add(searchCombo, BorderLayout.CENTER);
        dialog.add(topPanel, BorderLayout.NORTH);

        DefaultTableModel childModel = new DefaultTableModel(new String[]{"ID", "类型", "编号", "名称", "规格", "单位"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Long.class : String.class; }
        };
        JTable childTable = UIStyle.createTable(childModel);
        childTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        UIStyle.hideColumn(childTable, 0);
        try {
            for (Component c : loadProductChildCandidates()) {
                childModel.addRow(new Object[]{c.getId(), typeLabel(c.getType()), c.getCode(), c.getName(), c.getSpec(), c.getUnit()});
            }
        } catch (SQLException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage()); }

        searchCombo.setOnSelected(c -> {
            for (int i = 0; i < childModel.getRowCount(); i++) {
                if (c.getId().equals(childModel.getValueAt(i, 0))) {
                    int viewRow = childTable.convertRowIndexToView(i);
                    childTable.setRowSelectionInterval(viewRow, viewRow);
                    childTable.scrollRectToVisible(childTable.getCellRect(viewRow, 0, true));
                    return;
                }
            }
        });

        JScrollPane sp = UIStyle.wrap(childTable);
        sp.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(8, 10, 0, 10),
            sp.getBorder()));
        dialog.add(sp, BorderLayout.CENTER);

        JTextField qtyField = new JTextField("1", 6);
        JButton addBtn = UIStyle.primaryButton("添加");
        JButton cancelBtn = UIStyle.button("取消");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        bottom.setOpaque(false);
        bottom.add(new JLabel("用量"));
        bottom.add(qtyField);
        bottom.add(addBtn);
        bottom.add(cancelBtn);
        dialog.add(bottom, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> {
            int[] rows = childTable.getSelectedRows();
            if (rows.length == 0) { JOptionPane.showMessageDialog(dialog, "请选择一个或多个子件"); return; }
            double qty;
            try {
                qty = Double.parseDouble(qtyField.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效数量");
                return;
            }
            try {
                java.util.Set<Long> addedChildIds = new java.util.LinkedHashSet<>();
                for (int row : rows) {
                    Long childId = (Long) childModel.getValueAt(childTable.convertRowIndexToModel(row), 0);
                    bomItemDao.insert(new BomItem(productId, childId, qty));
                    addedChildIds.add(childId);
                }
                dialog.dispose();
                loadBomItems(addedChildIds);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage());
            }
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(addBtn);
        dialog.setVisible(true);
    }

    private void showEditQuantityDialog(Long bomId) {
        String input = JOptionPane.showInputDialog(this, "输入新用量:");
        if (input == null) return;
        try {
            double qty = Double.parseDouble(input.trim());
            if (qty <= 0) throw new NumberFormatException();
            BomItem item = new BomItem();
            item.setId(bomId);
            item.setQuantity(qty);
            bomItemDao.update(item);
            loadBomItems();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效数量");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
