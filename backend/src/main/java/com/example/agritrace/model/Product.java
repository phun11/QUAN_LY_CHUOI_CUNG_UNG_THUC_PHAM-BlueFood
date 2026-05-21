package com.example.agritrace.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Product {
    public Long productId;
    public Long farmId;
    public String productName;
    public String category;
    public String description;
    public BigDecimal price;
    public String status;
    public String imageUrl;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
