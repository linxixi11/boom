package com.bom.ui;

import com.bom.service.ComponentSheetService;
import com.bom.service.ComponentSheetService.ImportResult;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public final class ComponentSheetActions {
    private static final ComponentSheetService sheetService = new ComponentSheetService();

    private ComponentSheetActions() {}

    public static void exportAll(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件", "xlsx"));
        chooser.setSelectedFile(new File("BOM物料清单.xlsx"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getAbsolutePath() + ".xlsx");
        }

        try {
            sheetService.exportAllComponents(file);
            JOptionPane.showMessageDialog(parent, "导出成功: " + file.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "导出失败: " + ex.getMessage());
        }
    }

    public static boolean importAll(Component parent) {
        int confirm = JOptionPane.showConfirmDialog(parent,
            "导入会按 ID 或编号更新已有物料，也会新增表格中的新物料。\n确认导入？",
            "导入物料清单", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return false;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件", "xlsx", "xls"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return false;

        try {
            ImportResult result = sheetService.importComponents(chooser.getSelectedFile());
            JOptionPane.showMessageDialog(parent, result.summary());
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "导入失败: " + ex.getMessage());
            return false;
        }
    }
}
