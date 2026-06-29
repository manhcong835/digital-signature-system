# Digital Signature System

Ứng dụng ký số và xác minh tài liệu bằng RSA + SHA-512. Hỗ trợ quản lý tài liệu, cặp khóa và ký trực tiếp trên PDF.

## Công nghệ

- Backend: Java 21, Spring Boot, MySQL.
- Frontend: React, TypeScript, Vite, TailwindCSS.

## Cách chạy

Cài JDK 21, Maven 3.8+, Node.js 20+ và MySQL 8+.

Tại thư mục gốc, mở PowerShell và chạy:

```powershell
.\run.ps1
```

Nhập thông tin kết nối MySQL khi được hỏi. Script sẽ cài các thư viện cần thiết và khởi động backend, frontend.

- Giao diện: http://localhost:5173
- Backend: http://localhost:8080
