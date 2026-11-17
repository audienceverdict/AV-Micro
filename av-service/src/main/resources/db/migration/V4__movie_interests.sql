CREATE TABLE movie_interests (
    id VARCHAR(36) NOT NULL,
    movie_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_movie_interest_user UNIQUE (movie_id, user_id)
);

CREATE INDEX idx_movie_interest_count ON movie_interests (movie_id);
