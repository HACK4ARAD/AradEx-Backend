# AradEx-Backend — context pentru Claude Code

Backend-ul AradExperience (Hack4Arad 2026). Repo în organizația HACK4ARAD, frate cu `AradEx-Plan` (planul comun), `AradEx-Frontend`, `AradEx-Infrastructure` și `AradEx-Ops` (agenții Paperclip), toate în același folder. **Citește întâi `../AradEx-Plan/CLAUDE.md`** (regulile echipei) ; `../AradEx-Plan/STRUCTURE.md` descrie toate repo-urile. Documentația echipei e în `../AradEx-Plan/docs/`, designul în `../AradEx-Plan/design/`. Skill-urile (`be-module`, `seed-data`, `task-workflow`, `review-pr`) sunt într-un singur loc: `../AradEx-Plan/.claude/skills/`.

## Citește înainte de orice task
1. `../AradEx-Plan/docs/PLAN.md` — task-ul curent. 2. `../AradEx-Plan/docs/API.md` — contractul (aici se implementează exact; FE îl consumă). 3. `../AradEx-Plan/docs/DATA-MODEL.md` — tabele, enum-uri, migrări. 4. `../AradEx-Plan/docs/FEATURES.md` — regulile de business pe roluri. 5. `../AradEx-Plan/docs/ARAD-DATA.md` — datele reale din seed și ce e FICTIV.

## Stack
Java 21 · Spring Boot 3.3.5 · Gradle 8 (Groovy DSL, wrapper inclus) · PostgreSQL 16 · Flyway · Spring Security + JWT (jjwt 0.12) · springdoc · Lombok · teste: JUnit 5, Mockito, AssertJ, MockMvc, JSONAssert, Testcontainers. AI: Claude (Anthropic API) prin SDK-ul oficial `com.anthropic:anthropic-java:3.2.0` (aprobat; se adaugă în `app/build.gradle` la T12), configurat din `app.anthropic.*` (`ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, implicit `claude-haiku-5-5`); structured outputs (`outputConfig`) pentru răspunsurile JSON.

## Două module: `api` = contractul, `app` = aplicația
```
AradEx-Backend/
  settings.gradle · build.gradle (părinte: plugin-uri, Java 21, BOM Spring Boot, JUnit) · gradlew · Dockerfile · .env.example   (Postgres local: ../AradEx-Infrastructure/docker-compose.yml)
  api/   CONTRACTUL REST — bibliotecă Java simplă (spring-web + validation + swagger-annotations), fără runtime Spring Boot
    src/main/java/aradexperience/
      api/          interfețele controllerelor, una per feature: HealthApi, AuthApi, EventsApi… (@RequestMapping pe tip; @GetMapping/@PostMapping,
                    @Valid/@RequestBody/@PathVariable, @ResponseStatus, @Tag/@Operation pe metode). Controllerele din app le implementează.
      dto/<feature>/  record-uri cerere/răspuns (dto/auth, dto/events…); dto/common: PageResponse, HealthDto
      enums/        enum-urile folosite în DTO-uri (Role — cu VOLUNTEER pentru T18, BusinessCategory, EventCategory, TicketType…)
      annotations/  adnotări de validare custom (Code = regex pentru AX-…)
      exceptions/   ApiException (status + mesaj) + BadRequest/Unauthorized/NotFound/Conflict/Forbidden — contractul de erori, aruncate din app
    src/test/java/aradexperience/
      dto/<feature>/XTest   serializare: JSON-ul din src/test/resources/dto/<feature>/x.json e adevărul; deserializare + round-trip STRICT (DtoJson helper)
      dto/DtoValidationTest regulile jakarta de pe DTO-urile de cerere · api/ApiContractTest: fiecare interfață/metodă are rută, fără duplicate
  app/   APLICAȚIA Spring Boot — singurul modul rulabil; depinde de api
    src/main/java/aradexperience/
      Application.java         @SpringBootApplication în pachetul rădăcină → scanare implicită a tuturor layerelor
      controller/<feature>/    XController implements XApi (@RestController): AuthContext → service → mapper static → DTO. Zero logică.
      controller/handler/      GlobalExceptionHandler — toate erorile ca ProblemDetail RFC 7807 (400 validare cu errors[], 401, 403, 404, 409, 500)
      service/<feature>/       XService (@Service, @Transactional), unul per controller — regulile de business
      repository/<feature>/    XRepository extends JpaRepository, unul per entitate
      entity/<feature>/        entitățile JPA (Lombok @Getter @Setter)
      mapper/<feature>/        XMapper — CLASĂ UTILITARĂ (@UtilityClass din Lombok), layer separat ca să fie refolosită din mai multe service-uri
      config/                  AppProps (app.* din application.yml), SecurityConfig (rute × roluri, CORS, 401/403 ca ProblemDetail), OpenApiConfig
      security/                JwtService, JwtAuthFilter, CurrentUser, AuthContext (utilizatorul curent pentru controllere)
      util/                    Codes (coduri AX-…), Geo (haversine), Pages (Page → PageResponse) — tot clase utilitare statice
    src/main/resources/        application.yml, application-prod.yml, db/migration/V{n}__{feature}.sql + V{n}_1__seed_{feature}.sql
    src/test/java/aradexperience/
      controller/ControllerTest             adnotarea compusă pentru testele de controller (@WebMvcTest + SecurityConfig real + JwtService mock)
      controller/<feature>/XControllerTest  @ControllerTest(XController.class) + service mock; cererile din src/test/resources/requests/<feature>/*.json
      service/<feature>/XServiceTest        Mockito, fără Spring
      mock/                                 date de test refolosite: AuthMock (utilizator în SecurityContext), JsonFixtures (JSON din resources), XMock per service
      AppSmokeTest                          @SpringBootTest + Testcontainers: pornește tot, Flyway, /health, 401
```
Un feature nou (`events`) înseamnă: în `api` — `api/EventsApi`, `dto/events/*`, enum-urile lui; în `app` — câte un subfolder `events/` în `controller`, `service`, `repository`, `entity`, `mapper` + migrarea + testele. Subfolderele se creează pe măsură ce vin task-urile.
Regula de dependență: `app` → `api`, niciodată invers (`api` nu știe de entități, JPA sau Spring Security).

## Comenzi
```
(cd ../AradEx-Infrastructure && docker compose up -d)   # Postgres local (arad/arad, :5432)
./gradlew test                 # toate testele (api + app); AppSmokeTest cere Docker, altfel se sare singur
./gradlew :api:test            # doar testele contractului (secunde)
./gradlew :app:bootRun         # API pe :8080 · Swagger /swagger-ui.html · health /api/v1/health (încarcă .env automat)
./gradlew :app:bootJar && java -jar app/build/libs/app.jar
```
Secretele în `.env` (vezi `.env.example`); în IDE setezi aceleași variabile la Run Configuration. `application.yml` are valori implicite pentru local.

## Reguli
- Endpoint-uri și DTO-uri = exact `../AradEx-Plan/docs/API.md`; prefix `/api/v1` în `@RequestMapping` al interfeței. Dacă trebuie schimbat ceva, schimbă `API.md` în același PR și anunță FE (tipurile din `AradEx-Frontend/src/shared/types/api.ts` oglindesc DTO-urile).
- Interfața din `api/` poartă TOATE adnotările de web (mapping, `@Valid`, `@ResponseStatus`, `@Operation`); controllerul are doar `@RestController` și `@Override`. Fără `@AuthenticationPrincipal` în interfețe — controllerul ia utilizatorul cu `AuthContext.require()` / `AuthContext.current()` și îl pasează serviciului.
- Erori: serviciile aruncă `BadRequestException` (400), `UnauthorizedException` (401), `NotFoundException` (404), `ConflictException` (409), `ForbiddenException` (403) din `aradexperience.exceptions`; handler-ul le transformă în ProblemDetail. Validarea de formă stă pe DTO-uri (jakarta), validarea de business în service.
- **Mapper-ele și utilitarele sunt clase utilitare** adnotate cu `@lombok.experimental.UtilityClass` (Lombok le face `final`, cu constructor privat): `@UtilityClass public class XMapper { public static XDto toDto(X x) {...} }` — **metodele se declară explicit `static`** (altfel `import static` nu compilează: javac nu vede metodele făcute statice de Lombok) și **fără constructor propriu**; folosite prin `import static aradexperience.mapper.x.XMapper.toDto;`. Fără stare, fără dependențe. Exemplu: `mapper/users/UserMapper`.
- Utilizatori: emailul se stochează normalizat (`trim().toLowerCase(Locale.ROOT)`), căutarea e exactă (`UserRepository.findByEmail`). Rutele × roluri sunt toate în `SecurityConfig` (T03) și testate în `config/SecurityRulesTest`.
- Coduri: `Codes.generate("AX-T" + id)`. Geo: `Geo.distanceKm`. Timpi: `OffsetDateTime`, DB `timestamptz`, zona pentru „azi/weekend”: `Europe/Bucharest`. Paginare: `Pages.of(page, EventMapper::toSummary)`.
- Migrări doar în `app/src/main/resources/db/migration`; numărul următor liber, în ordinea story-urilor din `../AradEx-Plan/docs/PLAN.md` (verifică pe `origin/main` înainte); nu se modifică migrări deja pe `main`. Seed-urile sunt idempotente (`ON CONFLICT DO NOTHING` + `setval`).
- Punctele se scriu doar prin `service/points/PointsService`.
- Loguri `@Slf4j`, INFO la acțiuni de business, fără date personale.
- Dependențe noi doar în `app/build.gradle` (versiuni din BOM-ul Spring Boot; explicit doar jjwt, springdoc, swagger-annotations) și cu motivul notat în descrierea PR-ului.

## Teste (ce e obligatoriu)
- `api`: fiecare DTO nou are JSON în `src/test/resources/dto/<feature>/` + test de serializare (șablon `dto/auth/UserDtoTest`); DTO de cerere cu reguli → un caz în `DtoValidationTest`. `ApiContractTest` rămâne verde.
- `app`: test unitar pe fiecare Service cu logică (cazurile din „Verificare” ale task-ului), cu datele de test în `mock/XMock`; test de controller după șablonul `controller/health/HealthControllerTest` (`@ControllerTest(XController.class)` — adnotarea noastră = `@WebMvcTest` + `SecurityConfig` real + `JwtService` mock — plus `@MockBean XService`, utilizator cu `.with(AuthMock.as(id, rol))`, cererea din `requests/<feature>/*.json`, un caz 200/201 și un caz 4xx). `AppSmokeTest` rămâne verde.
- Nu dezactiva teste ca să treacă. `./gradlew test` înainte de PR.

## Nu
- Nu pune logică în controllere, nu întoarce entități din controllere, nu importa JPA/Security în `api`.
- Nu face mapper-ele `@Component`/injectate și nu le da dependențe.
- Nu schimba `SecurityConfig`, `config/`, `security/`, `util/`, `exceptions/`, `controller/handler/` sau `build.gradle` — sunt ale lui P1 (Zoltan); regulile pentru toate rutele din API.md vin din T03. Dacă îți lipsește ceva, cere-l.
- Nu `ddl-auto=update`; schema vine din Flyway.
- Nu `@EntityScan`/`@EnableJpaRepositories`/`scanBasePackages` pe `Application` — nu sunt necesare (pachetul rădăcină) și strică `@WebMvcTest`.

## Culoare (4 oameni în paralel)
Fiecare story lucrează doar în subfolderele feature-urilor lui, în fiecare layer (`api/<F>Api`, `dto/<f>/`, enum-urile lui, `controller|service|repository|entity|mapper/<f>/`, testele și `src/test/resources/{dto,requests}/<f>/`, `mock/<F>Mock`). Poți importa și apela codul altui story (service, repository, mapper), dar nu-l modifici.

## Commit-uri
Mesaje scurte în engleză (`feat(events): list + filters`). **Fără `Co-Authored-By:` și fără „Generated with Claude Code”** — hook-ul din `.githooks` le scoate (`git config core.hooksPath .githooks`). Branch `feat/Txx-...` din `origin/main`, PR în acest repo; regulile complete: `../AradEx-Plan/CLAUDE.md`.
