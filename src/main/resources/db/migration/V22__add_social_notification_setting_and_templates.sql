ALTER TABLE user_notification_setting
    ADD COLUMN social_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER weekly_enabled;

ALTER TABLE notification_template
    MODIFY COLUMN type ENUM('ENCOURAGE','LIKE','GOAL_NEAR','GOAL_COMPLETE','CERTIFICATION_CREATED') NOT NULL;

INSERT INTO notification_template (`type`, `sub_type`, title, body, created_at, updated_at)
SELECT 'CERTIFICATION_CREATED', NULL, '{nickname}님이 인증을 올렸어요 ✅', '함께라서 더 든든한 절약! 팀원의 새로운 절약 인증을 확인해보세요 👀 ', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_template WHERE `type` = 'CERTIFICATION_CREATED'
);

INSERT INTO notification_template (`type`, `sub_type`, title, body, created_at, updated_at)
SELECT 'LIKE', NULL, '{nickname}님이 회원님의 인증을 좋아해요 ❤️', '내 인증 게시물에 새로운 반응이 도착했어요 ⭐️', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_template WHERE `type` = 'LIKE'
);
