package com.example.b1void.models;

public class Inspection {

    private String goodsName;
    private String supplierName;
    private String inspectionDate;

    public Inspection(String goodsName, String supplierName, String inspectionDate) {
        this.goodsName = goodsName;
        this.supplierName = supplierName;
        this.inspectionDate = inspectionDate;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getInspectionDate() {
        return inspectionDate;
    }
}

