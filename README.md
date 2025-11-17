# Eventy Users Service

## Overview
Eventy Users Service is a Spring Boot microservice responsible for managing user profiles, moderation, and user-related data for the Eventy platform. Authentication and authorization are handled by Keycloak; this service focuses on business/user data only.

## Features
- User profile CRUD (create, read, update, delete)
- Admin moderation (suspend, delete users)
- Wallet balance endpoint
- JWT-based authentication (via Keycloak)
- PostgreSQL database integration
- RESTful API endpoints

## API Endpoints

### User Endpoints
- `GET /api/users/me` — Get current user's profile
- `PUT /api/users/me` — Update current user's profile
- `GET /api/users/me/balance` — Get current user's wallet balance

### Admin Endpoints
- `GET /api/admin/users` — List all users (with filters)
- `POST /api/admin/users/{id}/suspend` — Suspend a user
- `DELETE /api/admin/users/{id}` — Delete a user

## Environment Variables

Set these in your `.env` file (see `.env.example`):

- `DATABASE_URL` — PostgreSQL JDBC connection string (e.g. `jdbc:postgresql://host:port/dbname`)
- `JWT_ISSUER_URI` — Keycloak realm issuer URI (e.g. `http://keycloak-host:8080/realms/master`)

## Local Development

1. **Clone the repository:**
	```bash
	git clone <repo-url>
	cd eventy-users-service
	```
2. **Copy and edit environment file:**
	```bash
	cp .env.example .env
	# Edit .env with your DB and Keycloak info
	```
3. **Start with Docker Compose:**
	```bash
	docker compose up --build
	```
	This will start both the PostgreSQL database and the users service.

## Building & Running Manually

1. **Build the JAR:**
	```bash
	./mvnw clean package -DskipTests
	```
2. **Run the service:**
	```bash
	java -jar target/eventy-users-service-1.0.0.jar
	```

## Deployment (Dokku)

1. **Add Dokku remote:**
	```bash
	git remote add dokku dokku@cluster-ig5.igpolytech.fr:eventy-users-service
	```
2. **Set environment variables on Dokku:**
	```bash
	ssh dokku@cluster-ig5.igpolytech.fr config:set eventy-users-service DATABASE_URL=<your-db-url>
	ssh dokku@cluster-ig5.igpolytech.fr config:set eventy-users-service JWT_ISSUER_URI=<your-keycloak-issuer>
	```
3. **Deploy:**
	```bash
	git push dokku main
	```

## Project Structure

- `src/main/java/com/eventy/userservice/` — Main source code
- `src/main/resources/application.properties` — Spring Boot config
- `Dockerfile` — Container build instructions
- `docker-compose.yml` — Local dev orchestration
- `.env.example` — Example environment config

## Requirements
- Java 21 (Eclipse Temurin recommended)
- Maven (or Maven Wrapper)
- Docker & Docker Compose (for local dev)

## License
Licensed under the Apache License, Version 2.0.
