-- Empty-installation catalog baseline. Existing installations baseline at version 1.
-- Unrelated modules retain their existing Hibernate schema-management setting.
CREATE TABLE movies (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    poster_url VARCHAR(2000) NOT NULL,
    backdrop_url VARCHAR(2000),
    trailer_url VARCHAR(2000),
    description VARCHAR(2000),
    language VARCHAR(80) NOT NULL,
    duration INTEGER NOT NULL,
    release_date DATE,
    certification VARCHAR(30),
    director VARCHAR(150),
    production VARCHAR(150),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE movie_genres (movie_id VARCHAR(36) NOT NULL, genre VARCHAR(80), FOREIGN KEY (movie_id) REFERENCES movies(id));
CREATE TABLE movie_cast (movie_id VARCHAR(36) NOT NULL, cast_name VARCHAR(150), FOREIGN KEY (movie_id) REFERENCES movies(id));
CREATE TABLE movie_posters (movie_id VARCHAR(36) NOT NULL, poster_order INTEGER NOT NULL, poster_url MEDIUMTEXT NOT NULL, PRIMARY KEY(movie_id, poster_order), FOREIGN KEY (movie_id) REFERENCES movies(id));
