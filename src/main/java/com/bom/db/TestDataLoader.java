package com.bom.db;

import com.bom.dao.BomItemDao;
import com.bom.dao.ComponentDao;
import com.bom.model.BomItem;
import com.bom.model.Component;

import java.sql.SQLException;
import java.util.List;

public class TestDataLoader {

    public static void loadIfEmpty() throws SQLException {
        ComponentDao dao = new ComponentDao();
        List<Component> all = dao.findAll();
        if (!all.isEmpty()) return;

        BomItemDao bomDao = new BomItemDao();

        // ============ 零件 (PART) ============
        long lj = 0;
        Component[] parts = {
            c(Component.TYPE_PART, "LJ-001", "碳钢板",       "Q235 2mm",       "张",   "碳钢"),
            c(Component.TYPE_PART, "LJ-002", "不锈钢板",     "304 1.5mm",      "张",   "不锈钢"),
            c(Component.TYPE_PART, "LJ-003", "铝合金型材",   "6063-T5 40x40",  "米",   "铝合金"),
            c(Component.TYPE_PART, "LJ-004", "六角螺栓",     "M8x30",          "个",   "碳钢"),
            c(Component.TYPE_PART, "LJ-005", "六角螺母",     "M8",             "个",   "碳钢"),
            c(Component.TYPE_PART, "LJ-006", "平垫圈",       "M8",             "个",   "碳钢"),
            c(Component.TYPE_PART, "LJ-007", "弹簧垫圈",     "M8",             "个",   "碳钢"),
            c(Component.TYPE_PART, "LJ-008", "圆柱销",       "6x20",           "个",   "碳钢"),
            c(Component.TYPE_PART, "LJ-009", "密封圈",       "O型 50x3.5",     "个",   "橡胶"),
            c(Component.TYPE_PART, "LJ-010", "轴承",         "6205-2RS",       "个",   "轴承钢"),
            c(Component.TYPE_PART, "LJ-011", "电机",         "Y90S-4 1.1kW",   "台",   null),
            c(Component.TYPE_PART, "LJ-012", "减速机",       "WPDS-60 i=20",   "台",   null),
            c(Component.TYPE_PART, "LJ-013", "联轴器",       "LX28 Y型",       "个",   "铸铁"),
            c(Component.TYPE_PART, "LJ-014", "链条",         "10A-1",          "米",   "碳钢"),
            c(Component.TYPE_PART, "LJ-015", "链轮",         "Z=17 10A",       "个",   "碳钢"),
            c(Component.TYPE_PART, "LJ-016", "气缸",         "SC63x150",       "个",   null),
            c(Component.TYPE_PART, "LJ-017", "电磁阀",       "4V210-08",       "个",   null),
            c(Component.TYPE_PART, "LJ-018", "PLC控制器",    "S7-200SMART",    "台",   null),
            c(Component.TYPE_PART, "LJ-019", "传感器",       "E2B-M18KS",      "个",   null),
            c(Component.TYPE_PART, "LJ-020", "按钮开关",     "LA38-11",        "个",   null),
        };
        for (Component p : parts) { dao.insert(p); }

        // ============ 半成品 (SEMI) ============
        // 一级半成品：由零件组成
        long semiId1 = insertSemi(dao, "BC-001", "底板组件",       "800x600x10");
        bomDao.insert(new BomItem(semiId1, findId(dao, "LJ-001"), 2));   // 碳钢板 x2
        bomDao.insert(new BomItem(semiId1, findId(dao, "LJ-004"), 8));   // 螺栓 x8
        bomDao.insert(new BomItem(semiId1, findId(dao, "LJ-005"), 8));   // 螺母 x8
        bomDao.insert(new BomItem(semiId1, findId(dao, "LJ-006"), 16));  // 平垫圈 x16

        long semiId2 = insertSemi(dao, "BC-002", "侧板组件",       "600x400x10");
        bomDao.insert(new BomItem(semiId2, findId(dao, "LJ-001"), 4));   // 碳钢板 x4
        bomDao.insert(new BomItem(semiId2, findId(dao, "LJ-004"), 12));  // 螺栓 x12
        bomDao.insert(new BomItem(semiId2, findId(dao, "LJ-005"), 12));  // 螺母 x12
        bomDao.insert(new BomItem(semiId2, findId(dao, "LJ-007"), 12));  // 弹垫 x12

        long semiId3 = insertSemi(dao, "BC-003", "传动轴组件",     "φ30x500");
        bomDao.insert(new BomItem(semiId3, findId(dao, "LJ-010"), 2));   // 轴承 x2
        bomDao.insert(new BomItem(semiId3, findId(dao, "LJ-008"), 2));   // 销 x2
        bomDao.insert(new BomItem(semiId3, findId(dao, "LJ-015"), 1));   // 链轮 x1

        long semiId4 = insertSemi(dao, "BC-004", "驱动单元",       null);
        bomDao.insert(new BomItem(semiId4, findId(dao, "LJ-011"), 1));   // 电机 x1
        bomDao.insert(new BomItem(semiId4, findId(dao, "LJ-012"), 1));   // 减速机 x1
        bomDao.insert(new BomItem(semiId4, findId(dao, "LJ-013"), 1));   // 联轴器 x1

        long semiId5 = insertSemi(dao, "BC-005", "链传动组件",     null);
        bomDao.insert(new BomItem(semiId5, findId(dao, "LJ-014"), 2));   // 链条 x2米
        bomDao.insert(new BomItem(semiId5, findId(dao, "LJ-015"), 2));   // 链轮 x2

        long semiId6 = insertSemi(dao, "BC-006", "气动夹持器",     null);
        bomDao.insert(new BomItem(semiId6, findId(dao, "LJ-016"), 2));   // 气缸 x2
        bomDao.insert(new BomItem(semiId6, findId(dao, "LJ-009"), 4));   // 密封圈 x4

        long semiId7 = insertSemi(dao, "BC-007", "电气控制箱",     null);
        bomDao.insert(new BomItem(semiId7, findId(dao, "LJ-018"), 1));   // PLC x1
        bomDao.insert(new BomItem(semiId7, findId(dao, "LJ-020"), 4));   // 按钮 x4
        bomDao.insert(new BomItem(semiId7, findId(dao, "LJ-019"), 3));   // 传感器 x3

        // ============ 成品 (PRODUCT) ============
        // 成品只由半成品组成
        long prodId1 = insertProduct(dao, "CP-001", "A型输送机",     "带宽500mm");
        bomDao.insert(new BomItem(prodId1, semiId1, 1));                // 底板组件 x1
        bomDao.insert(new BomItem(prodId1, semiId2, 2));                // 侧板组件 x2
        bomDao.insert(new BomItem(prodId1, semiId4, 1));                // 驱动单元 x1
        bomDao.insert(new BomItem(prodId1, semiId5, 1));                // 链传动组件 x1
        bomDao.insert(new BomItem(prodId1, semiId6, 2));                // 气动夹持器 x2
        bomDao.insert(new BomItem(prodId1, semiId7, 1));                // 电气控制箱 x1

        long prodId2 = insertProduct(dao, "CP-002", "B型输送机",     "带宽800mm");
        bomDao.insert(new BomItem(prodId2, semiId1, 1));                // 底板组件 x1
        bomDao.insert(new BomItem(prodId2, semiId2, 2));                // 侧板组件 x2
        bomDao.insert(new BomItem(prodId2, semiId3, 2));                // 传动轴组件 x2
        bomDao.insert(new BomItem(prodId2, semiId4, 2));                // 驱动单元 x2
        bomDao.insert(new BomItem(prodId2, semiId5, 2));                // 链传动组件 x2
        bomDao.insert(new BomItem(prodId2, semiId6, 4));                // 气动夹持器 x4
        bomDao.insert(new BomItem(prodId2, semiId7, 1));                // 电气控制箱 x1

        long prodId3 = insertProduct(dao, "CP-003", "分拣工作站",     null);
        bomDao.insert(new BomItem(prodId3, semiId6, 6));                // 气动夹持器 x6
        bomDao.insert(new BomItem(prodId3, semiId7, 2));                // 电气控制箱 x2

        long prodId4 = insertProduct(dao, "CP-004", "自动装配线",     "A型+分拣站组合");
        bomDao.insert(new BomItem(prodId4, semiId1, 2));                // 底板组件 x2
        bomDao.insert(new BomItem(prodId4, semiId2, 4));                // 侧板组件 x4
        bomDao.insert(new BomItem(prodId4, semiId4, 2));                // 驱动单元 x2
        bomDao.insert(new BomItem(prodId4, semiId5, 2));                // 链传动组件 x2
        bomDao.insert(new BomItem(prodId4, semiId6, 8));                // 气动夹持器 x8
        bomDao.insert(new BomItem(prodId4, semiId7, 3));                // 电气控制箱 x3

        System.out.println("测试数据加载完成: 20个零件, 7个半成品, 4个成品");
    }

    private static Component c(String type, String code, String name, String spec, String unit, String material) {
        Component comp = new Component(type, code, name, spec, unit, material, null);
        return comp;
    }

    private static long insertSemi(ComponentDao dao, String code, String name, String spec) throws SQLException {
        Component c = new Component(Component.TYPE_SEMI, code, name, spec, null, null, null);
        dao.insert(c);
        return c.getId();
    }

    private static long insertProduct(ComponentDao dao, String code, String name, String spec) throws SQLException {
        Component c = new Component(Component.TYPE_PRODUCT, code, name, spec, null, null, null);
        dao.insert(c);
        return c.getId();
    }

    private static long findId(ComponentDao dao, String code) throws SQLException {
        List<Component> all = dao.findAll();
        for (Component c : all) {
            if (code.equals(c.getCode())) return c.getId();
        }
        throw new SQLException("找不到编号: " + code);
    }
}
