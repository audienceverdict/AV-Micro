ALTER TABLE social_comment_replies
 MODIFY COLUMN comment_id BIGINT NULL,
 ADD COLUMN content_id BIGINT NULL,
 ADD CONSTRAINT fk_outbound_comment_content FOREIGN KEY(content_id) REFERENCES social_content(id),
 ADD CONSTRAINT ck_outbound_comment_target CHECK ((comment_id IS NOT NULL AND content_id IS NULL) OR (comment_id IS NULL AND content_id IS NOT NULL));
