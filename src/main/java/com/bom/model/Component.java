package com.bom.model;

public class Component {
    public static final String TYPE_PART = "PART";
    public static final String TYPE_SEMI = "SEMI";
    public static final String TYPE_PRODUCT = "PRODUCT";

    private Long id;
    private String type;
    private String code;
    private String name;
    private String spec;
    private String unit;
    private String material;
    private String remark;
    private double stockQty;

    public Component() {}

    public Component(String type, String code, String name, String spec, String unit, String material, String remark) {
        this.type = type;
        this.code = code;
        this.name = name;
        this.spec = spec;
        this.unit = unit;
        this.material = material;
        this.remark = remark;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public double getStockQty() { return stockQty; }
    public void setStockQty(double stockQty) { this.stockQty = stockQty; }

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
