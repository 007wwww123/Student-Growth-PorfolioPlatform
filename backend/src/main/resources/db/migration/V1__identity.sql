CREATE TABLE IF NOT EXISTS arc_session (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    role_code VARCHAR(16) NOT NULL,
    student_ids JSON NOT NULL,
    academic_years JSON NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
