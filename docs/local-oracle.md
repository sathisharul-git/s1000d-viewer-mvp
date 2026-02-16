# Local Oracle XE (Docker)

Use Oracle XE for local development with Flyway migrations.

## Start Oracle

```powershell
docker compose up -d
```

Wait until container logs contain:

`DATABASE IS READY TO USE!`

## Connection details

- JDBC URL: `jdbc:oracle:thin:@localhost:1521/XEPDB1`
- Username: `s1000d_app`
- Password: `s1000d_secret`

These defaults match `application/src/main/resources/application.yml` and can be overridden with:

- `S1000D_DB_URL`
- `S1000D_DB_USER`
- `S1000D_DB_PASS`

## Stop Oracle

```powershell
docker compose down
```
