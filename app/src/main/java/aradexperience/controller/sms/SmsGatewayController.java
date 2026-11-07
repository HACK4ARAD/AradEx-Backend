package aradexperience.controller.sms;

import aradexperience.api.SmsGatewayApi;
import aradexperience.dto.sms.GatewaySmsDto;
import aradexperience.service.sms.SmsGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SmsGatewayController implements SmsGatewayApi {

    private final SmsGatewayService service;

    @Override
    public List<GatewaySmsDto> claim(String token) {
        return service.claim(token);
    }

    @Override
    public void sent(String token, long id) {
        service.sent(token, id);
    }

    @Override
    public void failed(String token, long id) {
        service.failed(token, id);
    }
}
