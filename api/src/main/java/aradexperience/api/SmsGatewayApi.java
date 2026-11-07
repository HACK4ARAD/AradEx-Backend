package aradexperience.api;

import aradexperience.dto.sms.GatewaySmsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * docs/API.md §SMS gateway (T24): doar pentru scriptul de pe Mac-ul care trimite codurile OTP prin iPhone (aplicația Mesaje).
 * Fără cont: se autentifică cu tokenul gateway-ului în headerul `X-Gateway-Token` (secretul `aradex/sms-gateway`); 401 altfel.
 */
@Tag(name = "SMS gateway")
@RequestMapping("/api/v1/sms-gateway")
public interface SmsGatewayApi {

    String TOKEN_HEADER = "X-Gateway-Token";

    @Operation(summary = "T24: următoarele SMS-uri de trimis (max 10); un SMS neconfirmat în 60 s se dă din nou (max 3 ori)")
    @PostMapping("/claim")
    List<GatewaySmsDto> claim(@RequestHeader(name = TOKEN_HEADER, required = false) String token);

    @Operation(summary = "T24: SMS-ul a plecat (textul cu codul se șterge)")
    @PostMapping("/messages/{id}/sent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sent(@RequestHeader(name = TOKEN_HEADER, required = false) String token, @PathVariable long id);

    @Operation(summary = "T24: SMS-ul nu s-a putut trimite (textul cu codul se șterge; omul cere alt cod)")
    @PostMapping("/messages/{id}/failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void failed(@RequestHeader(name = TOKEN_HEADER, required = false) String token, @PathVariable long id);
}
