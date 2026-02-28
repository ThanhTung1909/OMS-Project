
# Order Management System (OMS) - Microservices
Project Architecture Software Course
Một hệ thống quản lý đơn hàng thương mại điện tử (clone Shopee logic) được xây dựng dựa trên kiến trúc Microservices, hướng tới khả năng chịu tải cao và mở rộng dễ dàng.

# Kiến trúc hệ thống
Dự án được chia thành các service độc lập, giao tiếp với nhau qua REST API và Message Queue (RabbitMQ):
Service Name	        Port	Database	        Mô tả
Discovery Server	    8761	N/A	                Eureka Server (Quản lý danh sách service)
API Gateway	            8888	N/A	                Cổng vào duy nhất, định tuyến & xác thực
Identity Service	    8080	db_identity	        Đăng ký, Đăng nhập, JWT Token
Product Service	        8081	db_product	        Quản lý sản phẩm, danh mục
Inventory Service	    8082	db_inventory	    Quản lý kho, tồn kho, giữ hàng (reserve)
Order Service	        8083	db_order	        Quản lý đơn hàng, SAGA Orchestrator
Payment Service	        8084	db_payment	        Giả lập thanh toán
Notification Service	8085	db_notification	    Gửi email thông báo (Async via RabbitMQ)

# Hướng dẫn chạy dự án (Getting Started)
Bước 1: Clone dự án
- git clone <link-repo-cua-ban>
- cd OMS-Project
- git checkout develop

Bước 2: Khởi chạy Hạ tầng (Infrastructure)
# Tại thư mục gốc của dự án
- docker-compose up -d
- Chờ khoảng 1-2 phút để MariaDB khởi tạo 6 databases và RabbitMQ sẵn sàng.
    +  Check Eureka: http://localhost:8761 (Phải thấy các service list)
    +  Check RabbitMQ: http://localhost:15672 (User/Pass: user/password)

# Quy trình làm việc (Git Workflow)
Chúng ta tuân thủ nghiêm ngặt GitHub Flow.
1. Nguyên tắc cốt lõi
- CẤM push trực tiếp vào nhánh main và develop.
- Tất cả code phải thông qua Pull Request (PR).
- Mỗi task trên GitHub Project tương ứng với một nhánh riêng.
2. Các bước code một tính năng
- Nhận việc: Vào tab [Projects] trên GitHub, kéo thẻ từ Todo sang In Progress.
- Tạo nhánh: Từ nhánh develop, tạo nhánh mới:
    + Tính năng mới: feature/ten-tinh-nang (VD: feature/login-api)
    + Sửa lỗi: fix/ten-loi (VD: fix/db-connection)
- Commit: Viết message rõ ràng (Tiếng Anh hoặc Việt đều được, nhưng phải có prefix):
    + feat: ... (Tính năng mới)
    + fix: ... (Sửa lỗi)
    + refactor: ... (Sửa code cũ)
Tạo PR: Push nhánh lên GitHub và tạo Pull Request vào nhánh develop.
Review: Tag tên thành viên khác vào review. Khi có 1 Approve mới được Merge.

# Cấu trúc thư mục

OMS-Project/                (Root Git)
├── .github/                (CI/CD)
├── .gitignore              (File ignore chung)
├── docker/                 (Chứa file init.sql)
├── docker-compose.yml      (Chạy database, rabbitmq...)
├── README.md
│
backend/
│   ├── api-gateway/      # Spring Cloud Gateway
│   ├── discovery-server/ # Netflix Eureka
│   ├── identity-service/ # Auth Service
│   ├── product-service/  # Product Service
│   ├── order-service/    # Order Service
│   └── ...
│
└── frontend/               (Dự án ReactJS nằm ở đây)
    ├── package.json
    ├── src/
    ├── public/
    └── ...

