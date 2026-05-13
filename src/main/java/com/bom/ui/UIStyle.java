package com.bom.ui;

import com.bom.service.PreferenceService;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 科技风视觉系统：深蓝底 + 青色高光，自绘按钮、暗色表头、渐变 banner。
 */
public final class UIStyle {
    // —— 主色板 ——
    public static final Color BG          = new Color(0xEEF2F8);
    public static final Color CARD        = new Color(0xFFFFFF);
    public static final Color BORDER      = new Color(0xCBD5E1);
    public static final Color BORDER_SOFT = new Color(0xE2E8F0);
    public static final Color TEXT        = new Color(0x0F172A);
    public static final Color TEXT_MUTED  = new Color(0x64748B);

    // 科技色
    public static final Color HEADER_DARK  = new Color(0x0B1E3F);
    public static final Color HEADER_DARK2 = new Color(0x143C7A);
    public static final Color ACCENT       = new Color(0x22D3EE); // cyan
    public static final Color ACCENT_DK    = new Color(0x0891B2);
    public static final Color PRIMARY      = new Color(0x2563EB);
    public static final Color PRIMARY_DK   = new Color(0x1D4ED8);
    public static final Color DANGER       = new Color(0xDC2626);
    public static final Color DANGER_DK    = new Color(0xB91C1C);
    public static final Color SUCCESS      = new Color(0x10B981);

    // 表格
    public static final Color HEADER_BG   = new Color(0x0F2A52);
    public static final Color HEADER_FG   = new Color(0xE2ECFA);
    public static final Color SELECTION   = new Color(0xCFE3FB);
    public static final Color ZEBRA       = new Color(0xF7F9FC);

    // 字体
    public static final Font FONT       = bestFont(13, Font.PLAIN);
    public static final Font FONT_BOLD  = bestFont(13, Font.BOLD);
    public static final Font FONT_TITLE = bestFont(15, Font.BOLD);
    public static final Font FONT_TAB   = bestFont(14, Font.PLAIN);
    public static final Font FONT_BANNER= bestFont(20, Font.BOLD);
    public static final Font FONT_SMALL = bestFont(12, Font.PLAIN);

    private UIStyle() {}

    private static Font bestFont(int size, int style) {
        String[] candidates = {
            "PingFang SC", "Microsoft YaHei UI", "Microsoft YaHei",
            "Source Han Sans CN", "Noto Sans CJK SC",
            "Heiti SC", "SimHei", "Hiragino Sans GB",
            "Segoe UI", "SF Pro Text", "Dialog"
        };
        Set<String> available = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : candidates) {
            if (available.contains(name)) return new Font(name, style, size);
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    public static void install() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        UIManager.put("control",                         BG);
        UIManager.put("nimbusBase",                      HEADER_DARK);
        UIManager.put("nimbusBlueGrey",                  new Color(0xC7D2DE));
        UIManager.put("nimbusFocus",                     ACCENT);
        UIManager.put("nimbusSelectionBackground",       PRIMARY);
        UIManager.put("nimbusSelectedText",              Color.WHITE);
        UIManager.put("text",                            TEXT);
        UIManager.put("Table.alternateRowColor",         ZEBRA);
        UIManager.put("Table.gridColor",                 BORDER_SOFT);
        UIManager.put("Table[Enabled+Selected].textBackground", SELECTION);
        UIManager.put("TabbedPane.contentAreaColor",     CARD);
        UIManager.put("TabbedPane.selected",             CARD);
        UIManager.put("TabbedPane.background",           BG);
        UIManager.put("TitledBorder.titleColor",         TEXT);
        UIManager.put("OptionPane.messageFont",          FONT);
        UIManager.put("OptionPane.buttonFont",           FONT);
        UIManager.put("ProgressBar.foreground",          ACCENT);
        UIManager.put("ProgressBar.background",          new Color(0xDDE6F1));

        for (Object key : new ArrayList<>(UIManager.getDefaults().keySet())) {
            Object v = UIManager.get(key);
            if (v instanceof Font) {
                Font f = (Font) v;
                UIManager.put(key, FONT.deriveFont(f.getStyle()));
            }
        }
    }

    // —— 表格 ——
    public static JTable createTable(TableModel model) {
        JTable t = new JTable(model);
        t.setFont(FONT);
        t.setRowHeight(30);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(BORDER_SOFT);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionBackground(SELECTION);
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);

        JTableHeader h = t.getTableHeader();
        h.setDefaultRenderer(new DarkHeaderRenderer());
        h.setPreferredSize(new Dimension(0, 34));
        h.setReorderingAllowed(false);
        h.setBorder(BorderFactory.createEmptyBorder());
        return t;
    }

    private static class DarkHeaderRenderer extends DefaultTableCellRenderer {
        public DarkHeaderRenderer() {
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(FONT_BOLD);
            setForeground(HEADER_FG);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setOpaque(false);
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                       boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            return this;
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, HEADER_DARK, 0, h, HEADER_DARK2));
            g2.fillRect(0, 0, w, h);
            // 底部青色高光线
            g2.setColor(ACCENT);
            g2.fillRect(0, h - 2, w, 2);
            // 列分隔线
            g2.setColor(new Color(0xFFFFFF & 0x1FFFFFFF, true));
            g2.drawLine(w - 1, 6, w - 1, h - 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static JScrollPane wrap(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(CARD);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static void rememberDividerLocation(JSplitPane split, String key, int defaultLocation) {
        PreferenceService preferences = PreferenceService.getInstance();
        SwingUtilities.invokeLater(() -> {
            int savedLocation = preferences.getInt(key, defaultLocation);
            split.setDividerLocation(savedLocation > 0 ? savedLocation : defaultLocation);
        });
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            int location = split.getDividerLocation();
            if (location > 0) {
                preferences.putInt(key, location);
            }
        });
    }

    // —— 按钮 ——
    public static JButton primaryButton(String text) {
        return new GradientButton(text, PRIMARY, PRIMARY_DK, Color.WHITE);
    }

    public static JButton accentButton(String text) {
        return new GradientButton(text, ACCENT_DK, new Color(0x0E7490), Color.WHITE);
    }

    public static JButton dangerButton(String text) {
        return new GradientButton(text, DANGER, DANGER_DK, Color.WHITE);
    }

    public static JButton button(String text) {
        return new FlatButton(text);
    }

    /** 一颗强调按钮：渐变 + 圆角 + hover。 */
    public static class GradientButton extends JButton {
        private final Color c1, c2, fg;
        private boolean hover;
        public GradientButton(String text, Color c1, Color c2, Color fg) {
            super(text);
            this.c1 = c1; this.c2 = c2; this.fg = fg;
            setFont(FONT_BOLD);
            setForeground(fg);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(8, 18, 8, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int arc = 10;
            Color a = c1, b = c2;
            if (!isEnabled()) {
                a = new Color(0xB8C2D1); b = new Color(0x95A1B5);
            } else if (hover) {
                a = brighten(c1, 0.08f); b = brighten(c2, 0.04f);
            } else if (getModel().isPressed()) {
                a = darken(c1, 0.10f); b = darken(c2, 0.06f);
            }
            g2.setPaint(new GradientPaint(0, 0, a, 0, h, b));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            // 顶部高光
            g2.setColor(new Color(255, 255, 255, hover ? 60 : 40));
            g2.fillRoundRect(1, 1, w - 2, h / 2, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
        @Override
        protected void paintBorder(Graphics g) { /* 无边框 */ }
    }

    /** 浅色描边按钮，hover 时透出主色。 */
    public static class FlatButton extends JButton {
        private boolean hover;
        public FlatButton(String text) {
            super(text);
            setFont(FONT);
            setForeground(TEXT);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int arc = 10;
            g2.setColor(hover ? new Color(0xE9F1FB) : Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(hover ? PRIMARY : BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
        @Override
        protected void paintBorder(Graphics g) { /* 无边框 */ }
    }

    private static Color brighten(Color c, float k) {
        int r = Math.min(255, (int)(c.getRed()   + (255 - c.getRed())   * k));
        int g = Math.min(255, (int)(c.getGreen() + (255 - c.getGreen()) * k));
        int b = Math.min(255, (int)(c.getBlue()  + (255 - c.getBlue())  * k));
        return new Color(r, g, b);
    }
    private static Color darken(Color c, float k) {
        int r = Math.max(0, (int)(c.getRed()   * (1 - k)));
        int g = Math.max(0, (int)(c.getGreen() * (1 - k)));
        int b = Math.max(0, (int)(c.getBlue()  * (1 - k)));
        return new Color(r, g, b);
    }

    // —— 渐变科技 banner ——
    public static class TechBanner extends JPanel {
        private final String title;
        private final String subtitle;
        public TechBanner(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
            setOpaque(true);
            setPreferredSize(new Dimension(0, 64));
            setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // 渐变底
            g2.setPaint(new GradientPaint(0, 0, HEADER_DARK, w, 0, HEADER_DARK2));
            g2.fillRect(0, 0, w, h);
            // 网格点（轻微科技纹理）
            g2.setColor(new Color(255, 255, 255, 18));
            for (int x = 0; x < w; x += 22) {
                for (int y = 0; y < h; y += 18) {
                    g2.fillRect(x, y, 1, 1);
                }
            }
            // 右侧光弧
            g2.setPaint(new RadialGradientPaint(
                    new Point(w - 80, h / 2), 220,
                    new float[]{0f, 1f},
                    new Color[]{new Color(34, 211, 238, 90), new Color(34, 211, 238, 0)}));
            g2.fillRect(w - 320, 0, 320, h);
            // 文本
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_BANNER);
            FontMetrics fm = g2.getFontMetrics();
            int x = 18;
            int y = h / 2 + fm.getAscent() / 2 - fm.getDescent() - 6;
            g2.drawString(title, x, y);
            int titleW = fm.stringWidth(title);
            // 青色装饰条
            g2.setColor(ACCENT);
            g2.fillRect(x, y + 4, titleW, 2);
            // 副标题
            if (subtitle != null && !subtitle.isEmpty()) {
                g2.setFont(FONT_SMALL);
                g2.setColor(new Color(0xA7C0E0));
                g2.drawString(subtitle, x + titleW + 14, y);
            }
            // 底边线
            g2.setColor(ACCENT);
            g2.fillRect(0, h - 2, w, 2);
            g2.dispose();
        }
    }

    public static JComponent banner(String title, String subtitle) {
        return new TechBanner(title, subtitle);
    }

    // —— 容器 ——
    public static JPanel toolbar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setOpaque(false);
        return p;
    }

    public static JPanel section() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setOpaque(false);
        return p;
    }

    public static JPanel page() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return p;
    }

    public static JPanel card() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(CARD);
        p.setBorder(new RoundedLineBorder(BORDER, 10));
        return p;
    }

    public static Border padding(int v, int h) {
        return BorderFactory.createEmptyBorder(v, h, v, h);
    }

    public static Border roundedBorder() {
        return new RoundedLineBorder(BORDER, 10);
    }

    /** 圆角描边 + 内边距。 */
    public static class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int arc;
        public RoundedLineBorder(Color color, int arc) {
            this.color = color; this.arc = arc;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(8, 10, 8, 10); }
        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(8, 10, 8, 10); return i;
        }
    }

    // —— 标签 / Pill ——
    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, getHeight() / 2 - 7, 4, 14, 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(FONT_TITLE);
        l.setForeground(TEXT);
        l.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 6));
        return l;
    }

    public static JLabel hintLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    /** 计数徽标 / 状态药丸。 */
    public static JLabel pill(String text, Color bg, Color fg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setBackground(bg);
        l.setForeground(fg);
        l.setFont(FONT_BOLD.deriveFont(12f));
        l.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 12));
        return l;
    }

    public static JLabel countPill(String text) {
        return pill(text, new Color(0x103B73), new Color(0xCFE3FB));
    }

    // —— 行布局 ——
    public static JPanel buttonRow(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (JButton b : buttons) p.add(b);
        return p;
    }

    public static JPanel buttonRowRight(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        for (JButton b : buttons) p.add(b);
        return p;
    }

    public static void hideColumn(JTable table, int viewIndex) {
        TableColumn col = table.getColumnModel().getColumn(viewIndex);
        col.setMinWidth(0);
        col.setMaxWidth(0);
        col.setPreferredWidth(0);
    }

    @SuppressWarnings("unused")
    private static List<String> ignored() { return new ArrayList<>(); }
}
