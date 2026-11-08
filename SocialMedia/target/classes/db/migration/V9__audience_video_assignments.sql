CREATE TABLE video_user_assignments (
 id VARCHAR(36) PRIMARY KEY,
 video_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(36) NOT NULL,
 assigned_by VARCHAR(36) NOT NULL,
 assignment_source VARCHAR(32) NOT NULL,
 assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_audience_video FOREIGN KEY(video_id) REFERENCES application_videos(id),
 UNIQUE KEY uq_audience_video(video_id,user_id),
 KEY ix_assignment_user(user_id,assigned_at,id),
 KEY ix_assignment_actor(assigned_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
