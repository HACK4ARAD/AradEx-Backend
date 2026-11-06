# AradEx-Backend — Spring Boot

Repo-ul backend al AradExperience (Hack4Arad 2026). **Citește întâi `../AradEx-Ops/CLAUDE.md`** (regulile echipei, cine ce atinge) și documentele din `../AradEx-Ops/docs/` (PLAN, API, DATA-MODEL, TEAM). Postgres local: `../AradEx-Infrastructure` (`docker compose up -d`).

## Stack aprobat
Java 21 · Spring Boot 3.3+ · Maven (`mvn`, sau `./mvnw` după `mvn wrapper:wrapper`) · spring-web, data-jpa, validation, security, actuator · Flyway · PostgreSQL ·
springdoc-openapi · jjwt 0.12 · Lombok · teste: JUnit 5, Mockito, AssertJ, spring-boot-starter-test, Testcontainers (postgres).
Client HTTP pentru OpenAI: `RestClient` din Spring (fără SDK extern).

## Structură pe module (pachet per funcționalitate)
```
ro.aradexperience.api
  common/   config (SecurityConfig, CorsConfig, OpenApiConfig, AppProps), security (JwtService, JwtAuthFilter, CurrentUser),
            error (GlobalExceptionHandler, BadRequestException, NotFoundException, ConflictException, ForbiddenException), util (Codes, Geo)
  auth/     AuthController, AuthService, dto/
  users/    User, UserRepository, UserService, dto/UserDto
  places/   Place, GuidePoint, *Repository, PlaceService, PlaceController, AdminPlaceController, dto/
  events/   Event, SavedEvent, EventRepository (+ EventSpecifications), EventService, EventController, dto/
  tickets/  Ticket, TicketRepository, TicketService, TicketController
  business/ Business, Announcement, repos, BusinessService, BusinessController (/business/**), PublicBusinessController
  offers/   Offer, OfferRedemption, repos, OfferService, OfferController, BusinessOfferController
  community/ CommunityAction, CommunityRegistration, repos, CommunityService, CommunityController, CityController
  points/   PointsTransaction, PointsRepository, PointsService, LeaderboardService
  ai/       OpenAiClient, ChatService, ItineraryService, prompts/ (text), schemas/ (json), fallback/ (json), AiController
  itineraries/ Itinerary, ItineraryRepository, ItineraryController
  admin/    AdminController, AdminService
```
Într-un modul: `Controller` (doar validare + mapare DTO), `Service` (logica, `@Transactional`), `Repository` (Spring Data),
entități JPA, `dto/` cu `record`-uri. Mapare entitate↔DTO prin metode statice `XDto.from(entity)` — fără MapStruct.

## Convenții
- Endpoint-urile și DTO-urile = exact `../AradEx-Ops/docs/API.md`. Prefix global `/api/v1` (în `application.yml`: `spring.mvc.servlet.path` sau `@RequestMapping`).
- Erori: aruncă `BadRequestException("...")` (400), `NotFoundException("Event %d not found")` (404), `ConflictException("capacity")` (409), `ForbiddenException()` (403); handler-ul le face ProblemDetail. Validare cu `@Valid` + jakarta annotations pe DTO-urile de cerere.
- Utilizatorul curent: `@AuthenticationPrincipal CurrentUser user` (`id`, `role`, `email`). Verificarea de proprietar se face în service (`assertOwner`).
- Coduri: `Codes.generate("AX-T" + eventId)` → `AX-T12-7K3Q9Z` (A-Z0-9, fără 0/O/1/I).
- Geo: `Geo.distanceKm(lat1,lng1,lat2,lng2)` (haversine); filtrul `near` se face în SQL cu formula haversine sau în memorie dacă lista e < 500.
- Timpi: `OffsetDateTime` în entități/DTO, DB `timestamptz`. Zona pentru logică de „azi/weekend”: `Europe/Bucharest`.
- Paginare: `Pageable` → `PageResponse<T>` propriu (`content, page, size, totalElements`).
- Config: `AppProps` (`@ConfigurationProperties("app")`) cu `jwt.secret`, `jwt.expirationHours`, `cors.origins`, `openai.apiKey/model`, `events.hotParticipants=50`, `events.hotSaves=20`.
- Migrări: `src/main/resources/db/migration/V{n}__{modul}.sql` + `V{n}_1__seed_{modul}.sql`. Numerele sunt rezervate pe task în `../AradEx-Ops/docs/STATUS.md` (nu le schimba; extra: rezerva culoarului tău).
- Loguri: `@Slf4j`, nivel INFO pentru acțiuni de business (bilet emis, puncte acordate), fără date personale.

## Teste
- Obligatoriu: test unitar pe fiecare `Service` cu logică (Mockito pentru repo-uri): cazurile din „Verificare” ale task-ului.
- Opțional: un `@SpringBootTest` + Testcontainers per modul pentru endpoint-ul principal (`MockMvc`). Dacă Docker lipsește local, `@EnabledIfEnvironmentVariable(named="DOCKER_TESTS", matches="true")`.
- Numele testelor spun comportamentul: `register_duplicateEmail_returns409`.

## Nu
- Nu schimba `SecurityConfig` (e al lui P1/Zoltan): regulile pentru toate rutele din API.md vin din T03. Dacă îți lipsește una, o ceri.
- Nu pune logică în controllere, nu întoarce entități JPA din controllere.
- Nu `spring.jpa.hibernate.ddl-auto=update` — schema vine doar din Flyway (în teste poate fi `validate`).

## Commit-uri
Mesaje scurte în engleză (`feat(events): list + filters`). **Fără `Co-Authored-By:` și fără „Generated with Claude Code”** — hook-ul din `.githooks` le scoate (`git config core.hooksPath .githooks`). Branch `feat/Txx-...` din `origin/main`, PR în acest repo; regulile complete: `../AradEx-Ops/CLAUDE.md`.
