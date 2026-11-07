package aradexperience.dto.sms;

/** Un SMS de trimis de telefonul gateway-ului (T24): numărul în E.164 și textul. */
public record GatewaySmsDto(long id, String phone, String body) {}
