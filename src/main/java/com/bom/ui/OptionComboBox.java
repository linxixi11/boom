package com.bom.ui;

import com.bom.service.OptionService;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.SQLException;
import java.util.List;

/**
 * 可编辑下拉框，自动加载数据库中的选项列表。
 * 用户输入新值后自动保存到数据库。
 * 提供 getText() 方法以兼容项目中的调用习惯。
 */
public class OptionComboBox extends JComboBox<String> {
    private final OptionService optionService = new OptionService();
    private final String category;

    public OptionComboBox(String category, String initialValue) {
        this.category = category;
        setEditable(true);
        setFont(UIStyle.FONT);

        loadOptions();
        if (initialValue != null && !initialValue.isEmpty()) {
            setSelectedItem(initialValue);
        }

        // 当失去焦点且有新值时自动保存
        getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                saveNewOption();
            }
        });
    }

    /**
     * 获取当前选中/输入的文本值。
     * JComboBox 本身没有 getText()，这里封装一层方便调用。
     */
    public String getText() {
        Object item = getSelectedItem();
        return item == null ? "" : item.toString();
    }

    /**
     * 设置当前显示的文本值。
     * JComboBox 本身没有 setText()，这里封装一层方便调用。
     */
    public void setText(String text) {
        setSelectedItem(text == null ? "" : text);
    }

    public void loadOptions() {
        try {
            Object current = getSelectedItem();
            removeAllItems();
            List<String> options = optionService.getOptions(category);
            for (String opt : options) {
                addItem(opt);
            }
            if (current != null) {
                setSelectedItem(current);
            } else {
                setSelectedItem("");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveNewOption() {
        Object item = getSelectedItem();
        if (item != null) {
            String value = item.toString().trim();
            if (!value.isEmpty()) {
                boolean exists = false;
                for (int i = 0; i < getItemCount(); i++) {
                    if (value.equals(getItemAt(i))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    try {
                        optionService.addOption(category, value);
                        addItem(value);
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }
}
