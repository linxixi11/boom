package com.bom.ui;

import com.bom.model.Component;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可搜索的下拉选择框：输入关键字实时过滤，弹出列表选择。
 * 使用 Timer 防抖，避免拼音输入法组合中被弹窗打断。
 */
public class SearchableComboBox extends JPanel {
    private final JTextField textField;
    private final JPopupMenu popup;
    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private List<Component> allItems;
    private List<Component> filteredItems;
    private Component selected;
    private Consumer<Component> onSelected;
    private boolean suppressDocEvent = false;

    // 防抖定时器，300ms 延迟后才真正执行过滤弹出
    private final Timer debounceTimer;

    public SearchableComboBox(List<Component> items) {
        this.allItems = items != null ? items : new ArrayList<>();
        this.filteredItems = new ArrayList<>();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(Color.GRAY));

        textField = new JTextField();
        textField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(textField, BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);

        popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(280, 200));
        popup.add(sp);

        // 防抖 Timer：300ms 内连续输入不触发弹窗，避免拼音组合被打断
        debounceTimer = new Timer(300, e -> doShowPopup());
        debounceTimer.setRepeats(false);

        // 输入过滤（通过防抖 Timer 延迟触发）
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { schedulePopup(); }
            @Override public void removeUpdate(DocumentEvent e) { schedulePopup(); }
            @Override public void changedUpdate(DocumentEvent e) { schedulePopup(); }
        });

        // 点击弹出
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (selected == null) schedulePopup();
            }
        });
        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                schedulePopup();
            }
        });

        // 键盘导航
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    int idx = list.getSelectedIndex();
                    if (idx < listModel.size() - 1) list.setSelectedIndex(idx + 1);
                    list.ensureIndexIsVisible(list.getSelectedIndex());
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    int idx = list.getSelectedIndex();
                    if (idx > 0) list.setSelectedIndex(idx - 1);
                    list.ensureIndexIsVisible(list.getSelectedIndex());
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    confirmSelection();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false);
                }
            }
        });

        // 鼠标点击列表选择
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) confirmSelection();
            }
        });

        // 点击外部关闭弹窗
        textField.addComponentListener(new ComponentAdapter() {});
        SwingUtilities.invokeLater(() -> {
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) {
                w.addWindowFocusListener(new WindowAdapter() {
                    @Override
                    public void windowLostFocus(WindowEvent e) {
                        popup.setVisible(false);
                    }
                });
            }
        });
    }

    /**
     * 安排一次防抖弹窗：每次调用都会重置计时器，
     * 只有在最后一次调用 300ms 后才真正执行 doShowPopup()。
     */
    private void schedulePopup() {
        if (suppressDocEvent) return;
        debounceTimer.restart();
    }

    /**
     * 真正执行关键字过滤和弹出列表
     */
    private void doShowPopup() {
        String keyword = textField.getText().trim().toLowerCase();
        filteredItems.clear();
        listModel.clear();
        for (Component c : allItems) {
            String label = (c.getCode() != null ? c.getCode() : "") + " - " +
                           (c.getName() != null ? c.getName() : "") +
                           (c.getSpec() != null ? " (" + c.getSpec() + ")" : "");
            if (keyword.isEmpty() || label.toLowerCase().contains(keyword)) {
                filteredItems.add(c);
                listModel.addElement(label);
            }
        }
        if (!filteredItems.isEmpty()) {
            list.setSelectedIndex(0);
            popup.show(textField, 0, textField.getHeight());
            textField.requestFocusInWindow();
        } else {
            popup.setVisible(false);
        }
    }

    private void confirmSelection() {
        int idx = list.getSelectedIndex();
        if (idx >= 0 && idx < filteredItems.size()) {
            selected = filteredItems.get(idx);
            suppressDocEvent = true;
            textField.setText(selected.getCode() + " - " + selected.getName());
            suppressDocEvent = false;
            popup.setVisible(false);
            if (onSelected != null) onSelected.accept(selected);
        }
    }

    public void setItems(List<Component> items) {
        this.allItems = items != null ? items : new ArrayList<>();
    }

    public Component getSelected() {
        return selected;
    }

    public void setOnSelected(Consumer<Component> callback) {
        this.onSelected = callback;
    }

    public void clearSelection() {
        selected = null;
        suppressDocEvent = true;
        textField.setText("");
        suppressDocEvent = false;
        popup.setVisible(false);
    }

    public JTextField getTextField() {
        return textField;
    }
}
