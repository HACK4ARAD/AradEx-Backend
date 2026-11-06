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

Scheletul a fost scris în avans și **nu a fost compilat** în mediul în care a fost generat (Maven Central inaccesibil de acolo). Primul `./gradlew test` poate scoate mici erori de import/versiune — se repară cu Claude Code și se notează în PR. Migrările V1–V4 și seed-urile au fost verificate pe Postgres 16.

Mod de lucru: `CLAUDE.md` de aici + `../AradEx-Plan/CLAUDE.md`; skill-urile (`be-module`, `seed-data`, `task-workflow`, `review-pr`) sunt în `../AradEx-Plan/.claude/skills/`. Un task din `../AradEx-Plan/docs/PLAN.md` = un branch = un PR.
