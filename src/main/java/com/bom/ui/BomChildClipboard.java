package com.bom.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 子件引用剪贴板，用于在成品/半成品面板之间复制粘贴子件引用。
 * 使用静态变量实现跨面板共享。
 */
public final class BomChildClipboard {
    private BomChildClipboard() {}

    /**
     * 剪贴板中的一条子件引用
     */
    public static class ChildRef {
        public final Long childId;
        public final String type;
        public final String code;
        public final String name;
        public final String spec;
        public final String unit;
        public final double quantity;

        public ChildRef(Long childId, String type, String code, String name,
                        String spec, String unit, double quantity) {
            this.childId = childId;
            this.type = type;
            this.code = code;
            this.name = name;
            this.spec = spec;
            this.unit = unit;
            this.quantity = quantity;
        }
    }

    // 静态剪贴板，跨面板共享
    private static List<ChildRef> clipboard = new ArrayList<>();

    /**
     * 复制子件引用到剪贴板
     */
    public static void copy(List<ChildRef> refs) {
        clipboard = new ArrayList<>(refs);
    }

    /**
     * 获取剪贴板中的子件引用
     */
    public static List<ChildRef> paste() {
        return new ArrayList<>(clipboard);
    }

    /**
     * 剪贴板是否为空
     */
    public static boolean isEmpty() {
        return clipboard.isEmpty();
    }

    /**
     * 剪贴板中的子件数量
     */
    public static int size() {
        return clipboard.size();
    }
}
