package com.bom.model;

public class BomItem {
    private Long id;
    private Long parentId;
    private Long childId;
    private double quantity;

    // 关联查询用
    private String childCode;
    private String childName;
    private String childSpec;
    private String childType;
    private String childUnit;
    private String childMaterial;

    public BomItem() {}

    public BomItem(Long parentId, Long childId, double quantity) {
        this.parentId = parentId;
        this.childId = childId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public String getChildCode() { return childCode; }
    public void setChildCode(String childCode) { this.childCode = childCode; }
    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }
    public String getChildSpec() { return childSpec; }
    public void setChildSpec(String childSpec) { this.childSpec = childSpec; }
    public String getChildType() { return childType; }
    public void setChildType(String childType) { this.childType = childType; }
    public String getChildUnit() { return childUnit; }
    public void setChildUnit(String childUnit) { this.childUnit = childUnit; }
    public String getChildMaterial() { return childMaterial; }
    public void setChildMaterial(String childMaterial) { this.childMaterial = childMaterial; }
}
