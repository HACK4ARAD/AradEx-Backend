package aradexperience.api;

import aradexperience.dto.offers.OfferCreate;
import aradexperience.dto.offers.OfferDto;
import aradexperience.dto.offers.OfferSummary;
import aradexperience.dto.offers.RedemptionDto;
import aradexperience.dto.offers.UseCodeRequest;
import aradexperience.enums.OfferCategory;
import aradexperience.enums.RedemptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * docs/API.md §Oferte (T10): lista publică, detaliul, revendicarea cu cod, codurile mele; panoul firmei (`/business/offers`,
 * validarea codurilor). Plata cu puncte (`pointsPrice`) se debitează din T11; până atunci revendicarea e gratuită.
 * Implementat de `controller/offers/OffersController` în `app`.
 */
@Tag(name = "Offers")
@RequestMapping("/api/v1")
public interface OffersApi {

    @Operation(summary = "Oferte publicate și valabile azi; filtre: category, localProduct, points (cu puncte), near=lat,lng, q")
    @GetMapping("/offers")
    List<OfferSummary> list(@RequestParam(name = "category", required = false) OfferCategory category,
                            @RequestParam(name = "localProduct", required = false) Boolean localProduct,
                            @RequestParam(name = "points", required = false) Boolean points,
                            @RequestParam(name = "near", required = false) String near,
                            @RequestParam(name = "q", required = false) String q);

    @Operation(summary = "Detaliul unei oferte publicate (incrementează viewsCount); myRedemption dacă ești logat")
    @GetMapping("/offers/{id}")
    OfferDto get(@PathVariable("id") long id);

    @Operation(summary = "Revendică oferta → cod AX-O{id}-XXXXXX (ISSUED); 409 dacă ai deja un cod activ sau oferta nu e valabilă azi. "
            + "T11: `withPoints=true` plătește `pointsPrice` din punctele comunitare (409 insufficient_points); ofertele doar pe puncte se plătesc mereu cu puncte")
    @PostMapping("/offers/{id}/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    RedemptionDto redeem(@PathVariable("id") long id, @RequestParam(name = "withPoints", defaultValue = "false") boolean withPoints);

    @Operation(summary = "Codurile mele de oferte, cele mai noi primele")
    @GetMapping("/me/redemptions")
    List<RedemptionDto> myRedemptions();

    @Operation(summary = "Ofertele firmei mele (inclusiv ciorne)")
    @GetMapping("/business/offers")
    List<OfferDto> businessOffers();

    @Operation(summary = "Ofertă nouă; DRAFT dacă firma nu e verificată")
    @PostMapping("/business/offers")
    @ResponseStatus(HttpStatus.CREATED)
    OfferDto createOffer(@Valid @RequestBody OfferCreate request);

    @Operation(summary = "Editează / pune pe pauză (status DRAFT) o ofertă a firmei mele; 403 dacă e a altei firme")
    @PutMapping("/business/offers/{id}")
    OfferDto updateOffer(@PathVariable("id") long id, @Valid @RequestBody OfferCreate request);

    @Operation(summary = "Șterge o ofertă a firmei mele (o ascunde dacă are deja coduri); 403 dacă e a altei firme")
    @DeleteMapping("/business/offers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOffer(@PathVariable("id") long id);

    @Operation(summary = "Validează un cod la casă → USED; 404 cod necunoscut, 409 deja folosit / al altei firme")
    @PostMapping("/business/redemptions/use")
    RedemptionDto useCode(@Valid @RequestBody UseCodeRequest request);

    @Operation(summary = "Codurile ofertelor firmei mele (opțional după stare), cele mai noi primele")
    @GetMapping("/business/redemptions")
    List<RedemptionDto> businessRedemptions(@RequestParam(name = "status", required = false) RedemptionStatus status);
}
