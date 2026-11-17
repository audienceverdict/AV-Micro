ALTER TABLE movies MODIFY COLUMN poster_url VARCHAR(2000) NULL;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='original_title')=0, 'ALTER TABLE movies ADD COLUMN original_title VARCHAR(200)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='tagline')=0, 'ALTER TABLE movies ADD COLUMN tagline VARCHAR(300)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='original_language')=0, 'ALTER TABLE movies ADD COLUMN original_language VARCHAR(80)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='release_status')=0, 'ALTER TABLE movies ADD COLUMN release_status VARCHAR(30)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='production_status')=0, 'ALTER TABLE movies ADD COLUMN production_status VARCHAR(30)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='country_of_origin')=0, 'ALTER TABLE movies ADD COLUMN country_of_origin VARCHAR(100)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='distributor')=0, 'ALTER TABLE movies ADD COLUMN distributor VARCHAR(150)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='official_website')=0, 'ALTER TABLE movies ADD COLUMN official_website VARCHAR(2000)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='announcement_date')=0, 'ALTER TABLE movies ADD COLUMN announcement_date DATE', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='production_start_date')=0, 'ALTER TABLE movies ADD COLUMN production_start_date DATE', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='production_end_date')=0, 'ALTER TABLE movies ADD COLUMN production_end_date DATE', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='budget')=0, 'ALTER TABLE movies ADD COLUMN budget DECIMAL(19,2)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movies' AND column_name='currency_code')=0, 'ALTER TABLE movies ADD COLUMN currency_code VARCHAR(3)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movies' AND index_name='idx_movies_release')=0, 'CREATE INDEX idx_movies_release ON movies(release_status, release_date)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movies' AND index_name='idx_movies_production')=0, 'CREATE INDEX idx_movies_production ON movies(production_status)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movies' AND index_name='idx_movies_title')=0, 'CREATE INDEX idx_movies_title ON movies(title)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movies' AND index_name='idx_movies_status_language')=0, 'CREATE INDEX idx_movies_status_language ON movies(status, language)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movie_genres' AND index_name='idx_movie_genres_value')=0, 'CREATE INDEX idx_movie_genres_value ON movie_genres(genre, movie_id)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;

CREATE TABLE IF NOT EXISTS movie_languages (movie_id VARCHAR(36) NOT NULL, language_name VARCHAR(80) NOT NULL, PRIMARY KEY(movie_id, language_name), FOREIGN KEY(movie_id) REFERENCES movies(id) ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS movie_filming_locations (movie_id VARCHAR(36) NOT NULL, location_name VARCHAR(200) NOT NULL, PRIMARY KEY(movie_id, location_name), FOREIGN KEY(movie_id) REFERENCES movies(id) ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS movie_production_companies (movie_id VARCHAR(36) NOT NULL, company_name VARCHAR(150) NOT NULL, PRIMARY KEY(movie_id, company_name), FOREIGN KEY(movie_id) REFERENCES movies(id) ON DELETE CASCADE);

CREATE TABLE IF NOT EXISTS people (
    id VARCHAR(36) PRIMARY KEY, full_name VARCHAR(150) NOT NULL,
    biography VARCHAR(10000), profile_image_url VARCHAR(2000), date_of_birth DATE,
    nationality VARCHAR(100), official_website VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL
);
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='people' AND index_name='idx_people_name')=0, 'CREATE INDEX idx_people_name ON people(full_name)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
CREATE TABLE IF NOT EXISTS person_skills (person_id VARCHAR(36) NOT NULL, skill VARCHAR(100) NOT NULL, PRIMARY KEY(person_id, skill), FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS person_social_links (
    id VARCHAR(36) PRIMARY KEY, person_id VARCHAR(36) NOT NULL,
    platform VARCHAR(30) NOT NULL, profile_url VARCHAR(2000) NOT NULL,
    username VARCHAR(150), is_verified_official BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(person_id) REFERENCES people(id) ON DELETE CASCADE
);
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='person_social_links' AND index_name='idx_social_person')=0, 'CREATE INDEX idx_social_person ON person_social_links(person_id)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
CREATE TABLE IF NOT EXISTS movie_credits (
    id VARCHAR(36) PRIMARY KEY, movie_id VARCHAR(36) NOT NULL, person_id VARCHAR(36) NOT NULL,
    credit_type VARCHAR(10) NOT NULL, department VARCHAR(100), role_title VARCHAR(150) NOT NULL,
    character_name VARCHAR(150), character_description VARCHAR(2000), character_image_url VARCHAR(2000),
    character_category VARCHAR(30), character_occupation VARCHAR(150), character_relationships VARCHAR(10000),
    screen_time_minutes INTEGER, billing_order INTEGER, is_main_cast BOOLEAN NOT NULL DEFAULT FALSE,
    is_cameo BOOLEAN NOT NULL DEFAULT FALSE, is_voice_role BOOLEAN NOT NULL DEFAULT FALSE,
    role_key VARCHAR(64) NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_credit_role UNIQUE(movie_id, person_id, role_key),
    FOREIGN KEY(movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    FOREIGN KEY(person_id) REFERENCES people(id)
);
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movie_credits' AND index_name='idx_credit_movie_order')=0, 'CREATE INDEX idx_credit_movie_order ON movie_credits(movie_id, billing_order)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movie_credits' AND index_name='idx_credit_person')=0, 'CREATE INDEX idx_credit_person ON movie_credits(person_id)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
CREATE TABLE IF NOT EXISTS movie_media (
    id VARCHAR(36) PRIMARY KEY, movie_id VARCHAR(36) NOT NULL, media_type VARCHAR(40) NOT NULL,
    title VARCHAR(200), description VARCHAR(2000), media_url VARCHAR(2000) NOT NULL,
    thumbnail_url VARCHAR(2000), language VARCHAR(80), is_official BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE, display_order INTEGER NOT NULL DEFAULT 0,
    source_platform VARCHAR(80), published_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    primary_movie_id VARCHAR(36) GENERATED ALWAYS AS (CASE WHEN is_primary THEN movie_id ELSE NULL END),
    CONSTRAINT uk_media_primary UNIQUE(primary_movie_id),
    CONSTRAINT chk_primary_poster CHECK (NOT is_primary OR media_type IN ('POSTER','THEATRICAL_POSTER','FIRST_LOOK_POSTER','CHARACTER_POSTER')),
    FOREIGN KEY(movie_id) REFERENCES movies(id) ON DELETE CASCADE
);
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='movie_media' AND index_name='idx_media_movie_order')=0, 'CREATE INDEX idx_media_movie_order ON movie_media(movie_id, display_order)', 'SELECT 1'); PREPARE av_stmt FROM @sql; EXECUTE av_stmt; DEALLOCATE PREPARE av_stmt;
-- Intentionally no FK: retain remediation evidence even after a movie is deleted.
CREATE TABLE IF NOT EXISTS catalog_legacy_issues (
    id VARCHAR(36) PRIMARY KEY, movie_id VARCHAR(36) NOT NULL,
    field_name VARCHAR(80) NOT NULL, original_value MEDIUMTEXT NOT NULL,
    reason VARCHAR(200) NOT NULL, created_at TIMESTAMP(6) NOT NULL
);
