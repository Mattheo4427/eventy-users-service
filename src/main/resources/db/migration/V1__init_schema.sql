-- Création de la table users
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    balance DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    role VARCHAR(50) NOT NULL DEFAULT 'USER'
);

-- Index sur l'email pour optimiser la recherche
CREATE INDEX idx_users_email ON users(email);