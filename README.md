# AradEx-Backend

API-ul AradExperience: Spring Boot 3, Java 21, Maven, PostgreSQL 16, Flyway, JWT, springdoc. Hack4Arad 2026.

```
cp .env.example .env                                   # cheile de la Zoltan, pe privat
(cd ../AradEx-Infrastructure && docker compose up -d)  # Postgres local
set -a; source .env; set +a
mvn -q test                                            # compilează + teste
mvn spring-boot:run                                    # http://localhost:8080/api/v1/health · /swagger-ui.html
```
Schelet scris în avans și **necompilat** până la T01 (Zoltan). Migrările V1–V4 + seed-urile sunt verificate pe Postgres 16.

Repo-uri surori: `AradEx-Ops` (plan, docs, design, agenți), `AradEx-Frontend` (Vue PWA), `AradEx-Infrastructure` (Postgres local, deploy).
Regulile de lucru: `CLAUDE.md` și `../AradEx-Ops/CLAUDE.md`.
