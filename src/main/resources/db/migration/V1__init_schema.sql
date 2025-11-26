-- Création de la table users
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255),
    
    -- "date" est un mot clé SQL, l'utilisation des guillemets est recommandée pour éviter les conflits
    "date" DATE NOT NULL,
    
    -- Stockage des Enums sous forme de chaîne
    status VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    
    -- Correspond à BigDecimal(precision=10, scale=2)
    balance NUMERIC(10, 2) NOT NULL DEFAULT 0.00
);

-- Index pour optimiser les recherches
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);