/**
 * Mapper-e entity ↔ DTO (DTO-urile sunt în modulul api): LAYER separat, ca să poată fi folosite din mai multe service-uri/controllere.
 * Un mapper per feature (mapper.users.UserMapper), CLASĂ UTILITARĂ: final, constructor privat, doar metode statice (toDto, toSummary, toEntity),
 * folosite prin import static: `import static aradexperience.mapper.users.UserMapper.toDto;`.
 * Câmpurile calculate (distanță, isHot, saved) vin ca parametri, nu le calculează mapper-ul. Fără stare, fără dependențe de service/repository.
 */
package aradexperience.mapper;
