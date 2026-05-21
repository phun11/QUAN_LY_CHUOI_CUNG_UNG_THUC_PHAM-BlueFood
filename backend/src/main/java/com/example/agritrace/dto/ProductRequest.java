package com.example.agritrace.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductRequest {
    public Long farmId;
    public String productName;
    public String category;
    public String description;
    public BigDecimal price;
    public String imageUrl;
    public String cultivationPlace;
    public LocalDate sowingDate;
    public LocalDate harvestDate;
    public String productionProcess;
}
