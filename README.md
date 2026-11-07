# AradEx-Backend

Backend AradExperience (Hack4Arad 2026): Spring Boot 3 / Java 21, Gradle multi-modul — `api` = contractul REST (interfețe, DTO-uri, enum-uri, excepții), `app` = aplicația (controller / service / repository / entity / mapper + migrări Flyway), PostgreSQL 16.
Repo frate cu `AradEx-Plan` (documentația echipei, în `docs/`), `AradEx-Frontend`, `AradEx-Infrastructure` (Postgres local) și `AradEx-Ops` (agenții Paperclip), toate în același folder.

```
cp .env.example .env               # completează OPENAI_API_KEY, JWT_SECRET
(cd ../AradEx-Infrastructure && docker compose up -d)   # Postgres
./gradlew test                     # compilează + rulează testele (AppSmokeTest cere Docker, altfel se sare)
./gradlew :app:bootRun             # http://localhost:8080/api/v1/health · /swagger-ui.html (încarcă .env)
./gradlew :app:bootJar             # app/build/libs/app.jar
```
Conturi demo (din seed): `visitor@demo.ro`, `local@demo.ro`, `firma@demo.ro`, `primaria@demo.ro`, `admin@demo.ro` / `demo1234`.

Scheletul compilează și `./gradlew test` trece cu JDK 21. Migrările V1–V4_2 (conturi, firme, 12 obiective cu ghid și poze, 30 de evenimente) se aplică la `bootRun` și sunt verificate pe Postgres 16. Login-ul (`AuthController`) vine în T03.

Mod de lucru: `CLAUDE.md` de aici + `../AradEx-Plan/CLAUDE.md`; skill-urile (`be-module`, `seed-data`, `task-workflow`, `review-pr`) sunt în `../AradEx-Plan/.claude/skills/`. Un task din `../AradEx-Plan/docs/PLAN.md` = un branch = un PR.
