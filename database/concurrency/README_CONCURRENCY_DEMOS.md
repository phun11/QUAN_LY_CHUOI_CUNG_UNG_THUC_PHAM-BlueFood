# HoneyBee Trace - Ghi chú demo quản lý đồng thời

Thư mục này dùng để trình bày các hiện tượng đồng thời trong DBMS. Khi demo, nên mở 2 cửa sổ SQL Developer/SQL*Plus tương ứng Session 1 và Session 2.

## 1. Dirty Read
Oracle mặc định không cho dirty read ở mức READ COMMITTED. Vì vậy demo dirty read chủ yếu để giải thích: transaction chưa COMMIT ở Session 1 thì Session 2 không đọc được dữ liệu bẩn.

## 2. Non-repeatable Read
Một session đọc cùng một dòng nhiều lần, session khác update và commit ở giữa hai lần đọc. Ở READ COMMITTED, lần đọc sau có thể thấy dữ liệu mới. Có thể dùng SERIALIZABLE để tránh.

## 3. Phantom Read
Một session đọc tập dòng theo điều kiện, session khác insert dòng mới thỏa điều kiện và commit. Lần đọc sau có thể thấy dòng “ma”. Có thể dùng SERIALIZABLE hoặc khóa phạm vi phù hợp.

## 4. Lost Update
Hai session cùng đọc một VERSION_NO, sau đó cùng update. Hệ thống tránh lỗi này bằng VERSION_NO trong PRODUCTS và kiểm tra version trong PRC_CHANGE_BATCH_STATUS.

## 5. Deadlock
Hai session khóa tài nguyên theo thứ tự ngược nhau: Session 1 khóa PRODUCTS rồi chờ QR_CODES, Session 2 khóa QR_CODES rồi chờ PRODUCTS. Oracle sẽ phát hiện deadlock và rollback một statement. Cách tránh: luôn khóa bảng/dòng theo cùng thứ tự trong mọi procedure.

## 6. SELECT FOR UPDATE
Dùng khi nghiệp vụ bắt buộc chỉ một transaction được sửa dòng tại một thời điểm, ví dụ đổi trạng thái lô, bán QR tại POS, hoặc xác nhận giao hàng.
