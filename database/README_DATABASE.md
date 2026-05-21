# HoneyBee Trace Database

Thư mục này được tổ chức theo đúng trọng tâm môn DBMS.

## Nên dùng khi báo cáo/demo

- `oracle/`: schema Oracle nâng cấp, có ràng buộc, trigger append-only, function, procedure, view, index.
- `concurrency/`: mô phỏng Dirty Read, Non-repeatable Read, Phantom Read, Lost Update, Deadlock, Serializable, Read Only, Select For Update.
- `recovery/`: demo rollback/crash và giải thích Undo/Redo/Checkpoint.
- `performance/`: script Execution Plan và ghi chú tối ưu index/response time/throughput.

## Legacy

- `legacy_previous/`: các script cũ được giữ lại để tham khảo, không nên dùng làm bản chính khi nộp.

## Cách chạy Oracle setup

Trong SQL Developer, mở thư mục `database/oracle`, sau đó chạy:

```sql
@09_all_in_one.sql
```

Nếu công cụ không nhận đường dẫn tương đối, hãy chạy tuần tự:

1. `01_drop.sql`
2. `02_tables.sql`
3. `03_triggers.sql`
4. `04_functions.sql`
5. `05_procedures.sql`
6. `06_views.sql`
7. `07_indexes.sql`
8. `08_sample_data.sql`
