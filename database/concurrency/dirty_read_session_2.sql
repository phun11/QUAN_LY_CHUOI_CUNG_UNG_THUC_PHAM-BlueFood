-- Dirty Read demo - Session 2
-- Kỳ vọng: vẫn thấy status cũ, không thấy REVOKED chưa commit.
SELECT PRODUCT_ID, STATUS FROM PRODUCTS WHERE PRODUCT_ID = 1;
