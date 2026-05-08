package com.bom;

import com.bom.db.DatabaseManager;
import com.bom.ui.MainFrame;
import com.bom.ui.UIStyle;

import javax.swing.*;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        UIStyle.install();
        try {
            DatabaseManager.getInstance().initDatabase();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "数据库初始化失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            DatabaseManager.getInstance().close()
        ));

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
