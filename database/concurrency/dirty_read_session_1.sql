-- Dirty Read demo - Session 1
-- Oracle không cho dirty read. Session 2 sẽ không thấy dữ liệu chưa COMMIT.
UPDATE PRODUCTS SET STATUS = 'REVOKED' WHERE PRODUCT_ID = 1;
-- Không COMMIT vội. Chạy session 2 để đọc.
-- Sau khi demo xong:
ROLLBACK;
