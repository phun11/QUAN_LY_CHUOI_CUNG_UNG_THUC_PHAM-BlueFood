package com.example.agritrace.dto;

import java.math.BigDecimal;

public class TransportRequest {
    public Long productId;
    public Long transporterId;
    public String transportCompany;
    public String fromLocation;
    public String toLocation;
    public BigDecimal storageTemperature;
    public String status;
    public String note;
    public Long userId;
}
