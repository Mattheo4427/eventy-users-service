
## Keycloak Integration

This service uses Keycloak for authentication. To enable Keycloak integration:

1. Ensure Keycloak is running (see `docker-compose.yml`).
2. Set the JWT issuer URI in your `.env` file:
	```
	JWT_ISSUER_URI=http://keycloak:8080/realms/eventy
	```
3. In Keycloak, create a realm named `eventy` and a client for this service.
4. The service expects JWT tokens issued by Keycloak for authentication.

**Note:** You may need to configure Keycloak clients, roles, and users as needed for your environment.

