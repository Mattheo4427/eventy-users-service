-- V2__insert_users.sql
-- Synchronisation des utilisateurs avec l'export Keycloak (Realm)

INSERT INTO users (id, email, username, first_name, last_name, balance, created_at)
VALUES 
    -- 1. Utilisateur "Acheteur" (ID Keycloak: e83d023e...)
    -- Solde initial : 200.00 pour tester l'achat de billets
    ('e83d023e-63a7-4675-8f41-72ac9925bfe7', 'acheteur@eventy.com', 'acheteur', 'ache', 'teur', 200.00, NOW()),

    -- 2. Utilisateur "Super Admin" (ID Keycloak: b8c52bbb...)
    -- Solde : 0.00
    ('b8c52bbb-5591-4bf0-931d-6a609c3b578b', 'admin@eventy.com', 'super_admin', 'Super', 'Admin', 0.00, NOW()),

    -- 3. Utilisateur "Vendeur" (ID Keycloak: e8f04b6c...)
    -- Solde : 0.00 (pour tester la réception des fonds)
    ('e8f04b6c-2d60-4a96-862e-0687d3fa2564', 'vendeur@eventy.com', 'vendeur', 'Jack', 'Honey', 0.00, NOW()),

    -- 4. Utilisateur "Test" (ID Keycloak: c5cc1b5d...)
    ('c5cc1b5d-4efd-4b3b-85b4-03a169bb8e2a', 'test@gmail.com', 'testusername', 'test firstname', 'test lastname', 50.00, NOW())

ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    username = EXCLUDED.username;