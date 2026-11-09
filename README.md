# AradEx-Backend

Backend AradExperience (Hack4Arad 2026): Spring Boot 3 / Java 21, Gradle multi-modul — `api` = contractul REST (interfețe, DTO-uri, enum-uri, excepții), `app` = aplicația (controller / service / repository / entity / mapper + migrări Flyway), PostgreSQL 16.
Repo frate cu `AradEx-Plan` (documentația echipei, în `docs/`), `AradEx-Frontend`, `AradEx-Infrastructure` (Postgres local) și `AradEx-Ops` (agenții Paperclip), toate în același folder.

```
cp .env.example .env               # completează ANTHROPIC_API_KEY (Claude API), JWT_SECRET
(cd ../AradEx-Infrastructure && docker compose up -d)   # Postgres
./gradlew test                     # compilează + rulează testele (AppSmokeTest cere Docker, altfel se sare)
./gradlew :app:bootRun             # http://localhost:8080/api/v1/health · /swagger-ui.html (încarcă .env)
./gradlew :app:bootJar             # app/build/libs/app.jar
```
Conturi demo (din seed): `visitor@demo.ro`, `local@demo.ro`, `firma@demo.ro`, `primaria@demo.ro`, `admin@demo.ro` / `demo1234`.

Stare: scheletul (T01) compilează și `./gradlew test` trece cu JDK 21; migrările V1–V4_2 (conturi, firme, 12 obiective cu ghid și poze, 30 de evenimente) se aplică la `bootRun`, verificate pe Postgres 16. **T03 (auth & roluri) e implementat**: `POST /auth/register`, `POST /auth/login`, `GET/PATCH /me`, JWT, `SecurityConfig` cu regulile pentru toate rutele din `docs/API.md`; `./gradlew test` = 124 de teste (cu Docker pornit, inclusiv `AppSmokeTest` pe Postgres real). Starea tuturor story-urilor: `../AradEx-Plan/docs/STATUS.md`.
Probleme de mediu (TLS interceptat → `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, porturi ocupate → `PORT` / `POSTGRES_URL` în `.env`): `../AradEx-Plan/docs/SETUP.md`.

Mod de lucru: `CLAUDE.md` de aici + `../AradEx-Plan/CLAUDE.md`; skill-urile (`be-module`, `seed-data`, `task-workflow`, `review-pr`) sunt în `../AradEx-Plan/.claude/skills/`. Un task din `../AradEx-Plan/docs/PLAN.md` = un branch = un PR.

## Drepturi de autor

© 2026 Echipa AradExperience (Hack4Arad 2026): ZOCZELI, LukasL2004, vaidaoctav, DamAndrei04. **Toate drepturile rezervate.** Codul e public doar ca să poată fi văzut; copierea, modificarea sau folosirea lui în alt proiect cer acordul nostru scris. Detalii în [LICENSE](LICENSE). Pozele (Wikimedia Commons), datele de transport (CTP Arad) și bibliotecile open source rămân sub licențele lor.

*Copyright © 2026 the AradExperience team. All rights reserved — see [LICENSE](LICENSE).*
