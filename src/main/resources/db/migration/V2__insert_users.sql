-- V2__insert_users.sql
-- Synchronisation des utilisateurs avec le schéma spécifique du microservice Users

INSERT INTO users (
    user_id, 
    username, 
    email, 
    first_name, 
    last_name, 
    avatar_url, 
    "date", 
    status, 
    role, 
    balance
)
VALUES 
    -- 1. Utilisateur "Acheteur" (ID Keycloak: e83d023e...)
    (
        'e83d023e-63a7-4675-8f41-72ac9925bfe7', -- user_id
        'acheteur',                             -- username
        'acheteur@eventy.com',                  -- email
        'ache',                                 -- first_name
        'teur',                                 -- last_name
        NULL,                                   -- avatar_url
        CURRENT_DATE,                           -- "date"
        'ACTIVE',                               -- status
        'USER',                                 -- role
        0.00                                  -- balance
    ),

    -- 2. Utilisateur "Super Admin" (ID Keycloak: b8c52bbb...)
    (
        'b8c52bbb-5591-4bf0-931d-6a609c3b578b',
        'super_admin',
        'admin@eventy.com',
        'Super',
        'Admin',
        NULL,
        CURRENT_DATE,
        'ACTIVE',
        'ADMIN',                                -- Rôle ADMIN
        0.00
    ),

    -- 3. Utilisateur "Vendeur" (ID Keycloak: e8f04b6c...)
    (
        'e8f04b6c-2d60-4a96-862e-0687d3fa2564',
        'vendeur',
        'vendeur@eventy.com',
        'Jack',
        'Honey',
        NULL,
        CURRENT_DATE,
        'ACTIVE',
        'USER',
        0.00
    ),

    -- 4. Utilisateur "Test" (ID Keycloak: c5cc1b5d...)
    (
        'c5cc1b5d-4efd-4b3b-85b4-03a169bb8e2a',
        'testusername',
        'test@gmail.com',
        'test firstname',
        'test lastname',
        NULL,
        CURRENT_DATE,
        'ACTIVE',
        'USER',
        50.00
    )

-- Gestion des conflits (Mise à jour si l'ID existe déjà)
ON CONFLICT (user_id) DO UPDATE SET
    username = EXCLUDED.username,
    email = EXCLUDED.email,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    role = EXCLUDED.role,
    balance = EXCLUDED.balance;