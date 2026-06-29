CREATE INDEX idx_notification_inbox
	ON notification (user_id, status, created_at DESC, notification_id DESC);
