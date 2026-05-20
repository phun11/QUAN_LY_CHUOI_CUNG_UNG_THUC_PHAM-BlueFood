# Recovery explanation - HoneyBee Trace

## Tình huống lỗi
Khi tạo lô hàng, hệ thống phải thêm nhiều dữ liệu: `PRODUCTS`, `PRODUCT_ORIGIN`, `QR_CODES`, `BATCH_STATUS_HISTORY`, `AUDIT_LOGS`.
Nếu crash giữa chừng, dữ liệu có thể bị thiếu QR hoặc thiếu audit nếu không dùng transaction.

## Cách DBMS phục hồi
- **Undo log**: rollback các thay đổi chưa commit.
- **Redo log**: phục hồi các thay đổi đã commit nhưng chưa kịp ghi xuống datafile.
- **Checkpoint**: mốc giúp DBMS biết phần nào đã flush, giảm thời gian recovery.

## Thiết kế áp dụng
Các nghiệp vụ quan trọng được đặt trong procedure/transaction. Nếu lỗi xảy ra, rollback toàn bộ để đảm bảo atomicity.
