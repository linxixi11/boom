package com.bom.ui;

import com.bom.model.Component;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    private final PartPanel partPanel;
    private final PartPanel purchasePanel;
    private final SemiProductPanel semiProductPanel;
    private final ProductPanel productPanel;
    private final BomPanel bomPanel;
    private final OrderPanel orderPanel;
    private final SettingsPanel settingsPanel;

    public MainFrame() {
        setTitle("BOM 管理系统");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 560));
        UIStyle.rememberWindowBounds(this, "window.main.bounds", new Dimension(1180, 720), null);
        getContentPane().setBackground(UIStyle.BG);

        partPanel = new PartPanel();
        purchasePanel = new PartPanel(Component.TYPE_PURCHASE, "外购件库", "外购件");
        semiProductPanel = new SemiProductPanel();
        productPanel = new ProductPanel();
        bomPanel = new BomPanel();
        orderPanel = new OrderPanel();
        settingsPanel = new SettingsPanel();

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UIStyle.FONT_TAB);
        tabbedPane.setBackground(UIStyle.BG);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tabbedPane.addTab("零件库", partPanel);
        tabbedPane.addTab("外购件库", purchasePanel);
        tabbedPane.addTab("半成品", semiProductPanel);
        tabbedPane.addTab("成品", productPanel);
        tabbedPane.addTab("BOM 汇总", bomPanel);
        tabbedPane.addTab("订单", orderPanel);
        tabbedPane.addTab("设置", settingsPanel);

        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            switch (idx) {
                case 0: partPanel.refreshData(); break;
                case 1: purchasePanel.refreshData(); break;
                case 2: semiProductPanel.refreshData(); break;
                case 3: productPanel.refreshData(); break;
                case 4: bomPanel.refreshData(); break;
                case 5: orderPanel.refreshData(); break;
                case 6: settingsPanel.refreshData(); break;
            }
        });

        JComponent banner = UIStyle.banner("BOM 管理系统",
                "Bill of Materials · 零件 / 外购件 / 半成品 / 成品 / 汇总 / 订单 / 设置");
        add(banner, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
    }
}
