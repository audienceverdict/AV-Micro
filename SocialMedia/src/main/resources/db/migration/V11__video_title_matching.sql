-- NULL is retained for legacy duplicates so existing IDs and assignments stay valid.
-- Only the oldest video for a matching title receives the unique matching key.
ALTER TABLE application_videos
 ADD COLUMN title_match_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin,
 ADD UNIQUE KEY uq_application_video_title(title_match_key);

CREATE INDEX ix_video_assignment_audit_content ON application_video_assignment_audit(content_id);
