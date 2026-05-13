package com.bom.ui;

import com.bom.service.OptionService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class SettingsPanel extends JPanel {
    private final OptionService optionService = new OptionService();
    
    private final DefaultTableModel unitModel;
    private final JTable unitTable;
    private final DefaultTableModel materialModel;
    private final JTable materialTable;

    public SettingsPanel() {
        setLayout(new GridLayout(1, 2, 12, 0));
        setBackground(UIStyle.BG);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // 单位管理
        JPanel unitPanel = UIStyle.section();
        unitPanel.add(UIStyle.sectionLabel("单位管理"), BorderLayout.NORTH);
        
        unitModel = new DefaultTableModel(new String[]{"单位名称"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        unitTable = UIStyle.createTable(unitModel);
        unitPanel.add(UIStyle.wrap(unitTable), BorderLayout.CENTER);
        
        JPanel unitBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        unitBottom.setOpaque(false);
        JButton addUnitBtn = UIStyle.button("添加");
        JButton delUnitBtn = UIStyle.button("删除");
        unitBottom.add(addUnitBtn);
        unitBottom.add(delUnitBtn);
        unitPanel.add(unitBottom, BorderLayout.SOUTH);

        // 材质管理
        JPanel materialPanel = UIStyle.section();
        materialPanel.add(UIStyle.sectionLabel("材质管理"), BorderLayout.NORTH);
        
        materialModel = new DefaultTableModel(new String[]{"材质名称"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        materialTable = UIStyle.createTable(materialModel);
        materialPanel.add(UIStyle.wrap(materialTable), BorderLayout.CENTER);
        
        JPanel materialBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        materialBottom.setOpaque(false);
        JButton addMatBtn = UIStyle.button("添加");
        JButton delMatBtn = UIStyle.button("删除");
        materialBottom.add(addMatBtn);
        materialBottom.add(delMatBtn);
        materialPanel.add(materialBottom, BorderLayout.SOUTH);

        add(unitPanel);
        add(materialPanel);

        // 事件
        addUnitBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "请输入新单位:");
            if (name != null && !name.trim().isEmpty()) {
                try {
                    optionService.addOption(OptionService.CATEGORY_UNIT, name.trim());
                    refreshData();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "添加失败: " + ex.getMessage());
                }
            }
        });

        delUnitBtn.addActionListener(e -> {
            int row = unitTable.getSelectedRow();
            if (row >= 0) {
                String name = (String) unitModel.getValueAt(row, 0);
                try {
                    optionService.deleteOption(OptionService.CATEGORY_UNIT, name);
                    refreshData();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "删除失败: " + ex.getMessage());
                }
            }
        });

        addMatBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "请输入新材质:");
            if (name != null && !name.trim().isEmpty()) {
                try {
                    optionService.addOption(OptionService.CATEGORY_MATERIAL, name.trim());
                    refreshData();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "添加失败: " + ex.getMessage());
                }
            }
        });

        delMatBtn.addActionListener(e -> {
            int row = materialTable.getSelectedRow();
            if (row >= 0) {
                String name = (String) materialModel.getValueAt(row, 0);
                try {
                    optionService.deleteOption(OptionService.CATEGORY_MATERIAL, name);
                    refreshData();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "删除失败: " + ex.getMessage());
                }
            }
        });

        refreshData();
    }

    public void refreshData() {
        try {
            unitModel.setRowCount(0);
            List<String> units = optionService.getOptions(OptionService.CATEGORY_UNIT);
            for (String u : units) {
                unitModel.addRow(new Object[]{u});
            }

            materialModel.setRowCount(0);
            List<String> materials = optionService.getOptions(OptionService.CATEGORY_MATERIAL);
            for (String m : materials) {
                materialModel.addRow(new Object[]{m});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "加载设置失败: " + e.getMessage());
        }
    }
}
