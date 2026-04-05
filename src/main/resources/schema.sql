CREATE TABLE IF NOT EXISTS "users" (
    "user_id" VARCHAR(50) PRIMARY KEY,
    "password_hash" VARCHAR(100) NOT NULL,
    "created_at" TIMESTAMP NOT NULL
);
