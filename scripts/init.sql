-- 이커머스 데이터베이스 초기화 스크립트
-- MySQL 8.0

-- 데이터베이스가 없으면 생성 (Docker Compose에서 자동 생성되므로 생략 가능)
-- CREATE DATABASE IF NOT EXISTS ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ecommerce;

-- 권한 설정 (필요 시)
-- GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce_user'@'%';
-- FLUSH PRIVILEGES;

-- 테이블은 JPA Hibernate가 자동 생성하므로 여기서는 생략
-- ddl-auto: update 또는 create 사용

-- 초기 데이터 삽입 (선택 사항)

-- 카테고리 초기 데이터
-- INSERT INTO categories (name, description, created_at) VALUES
-- ('전자기기', '전자제품 카테고리', NOW()),
-- ('의류', '의류 카테고리', NOW()),
-- ('도서', '도서 카테고리', NOW());

-- 테스트용 사용자 (선택 사항)
-- INSERT INTO users (email, password, name, balance, role, status, created_at) VALUES
-- ('admin@example.com', '$2a$10$...', '관리자', 1000000, 'ADMIN', 'ACTIVE', NOW()),
-- ('user1@example.com', '$2a$10$...', '홍길동', 100000, 'USER', 'ACTIVE', NOW()),
-- ('user2@example.com', '$2a$10$...', '김철수', 50000, 'USER', 'ACTIVE', NOW());

-- 완료 메시지
SELECT 'Database initialization completed!' AS message;
