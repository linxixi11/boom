package com.bom.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public final class TablePrintSupport {
    private static final double A4_WIDTH = 595.0;
    private static final double A4_HEIGHT = 842.0;
    private static final int MAX_PREVIEW_PAGES = 200;

    private TablePrintSupport() {}

    public static PageFormat defaultLandscapeA4() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat format = job.defaultPage();
        Paper paper = new Paper();
        paper.setSize(A4_WIDTH, A4_HEIGHT);
        paper.setImageableArea(28, 28, A4_WIDTH - 56, A4_HEIGHT - 56);
        format.setPaper(paper);
        format.setOrientation(PageFormat.LANDSCAPE);
        return format;
    }

    public static PageFormat showPageSetup(Component parent, PageFormat currentFormat) {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat base = currentFormat == null ? defaultLandscapeA4() : currentFormat;
        return job.pageDialog(base);
    }

    public static void showPreview(Component parent, JTable table, PageFormat pageFormat, String title) {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(parent, "当前没有可预览的数据");
            return;
        }
        try {
            List<BufferedImage> pages = renderPages(table, pageFormat, title);
            if (pages.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "没有生成可预览页面");
                return;
            }
            PreviewDialog dialog = new PreviewDialog(SwingUtilities.getWindowAncestor(parent), title, pages);
            dialog.setVisible(true);
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(parent, "生成预览失败: " + ex.getMessage());
        }
    }

    public static void print(Component parent, JTable table, PageFormat pageFormat, String title) {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(parent, "当前没有可打印的数据");
            return;
        }
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            Printable printable = printableFor(table, title);
            job.setPrintable(printable, pageFormat == null ? defaultLandscapeA4() : pageFormat);
            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(parent, "打印失败: " + ex.getMessage());
        }
    }

    private static List<BufferedImage> renderPages(JTable table, PageFormat pageFormat, String title)
            throws PrinterException {
        PageFormat format = pageFormat == null ? defaultLandscapeA4() : pageFormat;
        Printable printable = printableFor(table, title);
        List<BufferedImage> pages = new ArrayList<>();
        int width = Math.max(1, (int) Math.ceil(format.getWidth()));
        int height = Math.max(1, (int) Math.ceil(format.getHeight()));

        for (int pageIndex = 0; pageIndex < MAX_PREVIEW_PAGES; pageIndex++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int exists = printable.print(g2, format, pageIndex);
            g2.dispose();
            if (exists == Printable.NO_SUCH_PAGE) {
                break;
            }
            pages.add(image);
        }
        return pages;
    }

    private static Printable printableFor(JTable table, String title) {
        MessageFormat header = new MessageFormat(title == null || title.trim().isEmpty() ? "打印清单" : title);
        MessageFormat footer = new MessageFormat("第 {0} 页");
        return table.getPrintable(JTable.PrintMode.FIT_WIDTH, header, footer);
    }

    private static class PreviewDialog extends JDialog {
        private final List<BufferedImage> pages;
        private final PagePanel pagePanel;
        private final JLabel pageLabel;
        private int pageIndex = 0;

        PreviewDialog(Window owner, String title, List<BufferedImage> pages) {
            super(owner, title + " - 打印预览", ModalityType.APPLICATION_MODAL);
            this.pages = pages;
            setLayout(new BorderLayout(8, 8));
            getContentPane().setBackground(UIStyle.BG);
            UIStyle.rememberWindowBounds(this, "dialog.print.preview.bounds", new Dimension(920, 720), owner);

            JButton prevBtn = UIStyle.button("上一页");
            JButton nextBtn = UIStyle.button("下一页");
            JButton zoomOutBtn = UIStyle.button("缩小");
            JButton zoomInBtn = UIStyle.button("放大");
            JButton closeBtn = UIStyle.button("关闭");
            pageLabel = UIStyle.hintLabel("");

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            toolbar.setOpaque(false);
            toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
            toolbar.add(prevBtn);
            toolbar.add(nextBtn);
            toolbar.add(zoomOutBtn);
            toolbar.add(zoomInBtn);
            toolbar.add(pageLabel);
            add(toolbar, BorderLayout.NORTH);

            pagePanel = new PagePanel();
            JScrollPane scrollPane = new JScrollPane(pagePanel);
            scrollPane.getViewport().setBackground(new Color(0xCBD5E1));
            scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            add(scrollPane, BorderLayout.CENTER);

            JPanel bottom = UIStyle.buttonRowRight(closeBtn);
            bottom.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
            add(bottom, BorderLayout.SOUTH);

            prevBtn.addActionListener(e -> {
                if (pageIndex > 0) {
                    pageIndex--;
                    updatePage();
                }
            });
            nextBtn.addActionListener(e -> {
                if (pageIndex < pages.size() - 1) {
                    pageIndex++;
                    updatePage();
                }
            });
            zoomOutBtn.addActionListener(e -> pagePanel.changeZoom(-0.1));
            zoomInBtn.addActionListener(e -> pagePanel.changeZoom(0.1));
            closeBtn.addActionListener(e -> dispose());

            updatePage();
        }

        private void updatePage() {
            pagePanel.setImage(pages.get(pageIndex));
            pageLabel.setText("第 " + (pageIndex + 1) + " / " + pages.size() + " 页");
        }
    }

    private static class PagePanel extends JPanel {
        private BufferedImage image;
        private double zoom = 0.9;

        PagePanel() {
            setBackground(new Color(0xCBD5E1));
        }

        void setImage(BufferedImage image) {
            this.image = image;
            revalidate();
            repaint();
        }

        void changeZoom(double delta) {
            zoom = Math.max(0.35, Math.min(2.0, zoom + delta));
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (image == null) return new Dimension(600, 400);
            return new Dimension((int) (image.getWidth() * zoom) + 48,
                    (int) (image.getHeight() * zoom) + 48);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int drawWidth = (int) (image.getWidth() * zoom);
            int drawHeight = (int) (image.getHeight() * zoom);
            int x = Math.max(24, (getWidth() - drawWidth) / 2);
            int y = 24;
            g2.setColor(new Color(0, 0, 0, 35));
            g2.fillRect(x + 6, y + 6, drawWidth, drawHeight);
            g2.setColor(Color.WHITE);
            g2.fillRect(x, y, drawWidth, drawHeight);
            g2.drawImage(image, x, y, drawWidth, drawHeight, null);
            g2.setColor(new Color(0x94A3B8));
            g2.drawRect(x, y, drawWidth, drawHeight);
            g2.dispose();
        }
    }
}
