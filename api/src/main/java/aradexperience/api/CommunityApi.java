package aradexperience.api;

import aradexperience.dto.community.ActionDto;
import aradexperience.dto.community.ActionSummary;
import aradexperience.dto.community.LeaderboardDto;
import aradexperience.dto.community.RegistrationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** docs/API.md §Comunitate & puncte (T11), partea localnicului. Implementat de `controller/community/CommunityController`. */
@Tag(name = "Community")
@RequestMapping("/api/v1")
public interface CommunityApi {

    @Operation(summary = "Acțiunile publicate (implicit doar cele care nu s-au încheiat), + myRegistration dacă e logat")
    @GetMapping("/community/actions")
    List<ActionSummary> actions(@RequestParam(defaultValue = "true") boolean upcoming);

    @Operation(summary = "Detaliul unei acțiuni; ciornele doar pentru Primărie / Admin (altfel 404)")
    @GetMapping("/community/actions/{id}")
    ActionDto action(@PathVariable long id);

    @Operation(summary = "Mă înscriu (LOCAL, VISITOR); 409 plin / deja înscris / acțiune încheiată")
    @PostMapping("/community/actions/{id}/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationDto register(@PathVariable long id);

    @Operation(summary = "Anulez înscrierea (idempotent); 409 dacă a fost deja confirmată")
    @DeleteMapping("/community/actions/{id}/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable long id);

    @Operation(summary = "Înscrierile mele (fără cele anulate), cele mai apropiate primele")
    @GetMapping("/me/community/registrations")
    List<RegistrationDto> myRegistrations();

    @Operation(summary = "Clasamentul lunii sau al anului (doar puncte câștigate); top 50 + poziția mea")
    @GetMapping("/community/leaderboard")
    LeaderboardDto leaderboard(@RequestParam(defaultValue = "month") String period);
}
