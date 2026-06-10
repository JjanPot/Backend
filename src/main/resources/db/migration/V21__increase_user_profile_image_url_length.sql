-- 프로필 이미지 URL 저장 길이 부족으로 인한 DB 에러 방지
-- 운영 DB 스키마와 JPA 엔티티 길이 일치
-- S3/CDN 이미지 URL 확장 가능성 대응
ALTER TABLE users
    MODIFY COLUMN profile_image_url VARCHAR(1024) NULL;
