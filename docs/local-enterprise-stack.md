# Local Enterprise Stack (Oracle + OpenLDAP + OPA)

This project supports a local enterprise stack using Docker Compose:

- Oracle XE (metadata DB)
- OpenLDAP (authentication and role groups)
- OPA (authorization policy decisions)

## 1) Start infrastructure

```powershell
docker compose up -d
```

## 2) Wait for services

Oracle readiness:

```powershell
docker logs s1000d-oracle-xe --tail 200
```

Wait until logs include `DATABASE IS READY TO USE!`.

OPA health:

```powershell
Invoke-WebRequest http://localhost:8181/health | Select-Object -ExpandProperty StatusCode
```

LDAP base check (optional):

```powershell
docker exec s1000d-openldap ldapsearch -x -H ldap://localhost:389 -D "cn=admin,dc=s1000d,dc=org" -w admin -b "dc=s1000d,dc=org" "(objectClass=*)"
```

## 3) Backend runtime settings

Defaults in `application/src/main/resources/application.yml` match Docker:

- `S1000D_DB_URL=jdbc:oracle:thin:@localhost:1521/XEPDB1`
- `S1000D_DB_USER=s1000d_app`
- `S1000D_DB_PASS=s1000d_secret`
- `S1000D_LDAP_URL=ldap://localhost:389`
- `S1000D_LDAP_BASE_DN=dc=s1000d,dc=org`
- `S1000D_LDAP_MANAGER_DN=cn=admin,dc=s1000d,dc=org`
- `S1000D_LDAP_MANAGER_PASS=admin`
- `S1000D_OPA_URL=http://localhost:8181`

## 4) Run backend

```powershell
./gradlew :application:bootRun
```

## 5) Run webapp

```powershell
cd webapp
npm install
npm run dev
```

## 6) LDAP test credentials

Users seeded from `dev/ldap/bootstrap.ldif`:

- `admin / admin123` -> LDAP group `s1000d-admin`
- `eng / eng123` -> LDAP group `s1000d-engineer`
- `view / view123` -> LDAP group `s1000d-viewer`

Mapped app roles:

- `s1000d-admin` -> `ROLE_ADMIN`
- `s1000d-engineer` -> `ROLE_ENGINEER`
- `s1000d-viewer` -> `ROLE_VIEWER`

## 7) Stop stack

```powershell
docker compose down
```

