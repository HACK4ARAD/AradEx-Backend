package aradexperience.dto.transit;

import aradexperience.enums.TransitMode;

import java.util.List;

/** O linie CTP (T22). `id` = routeId din Telelink, `shortName` = numărul afișat („1”, „39b”); culorile ca `#rrggbb`, pot lipsi. */
public record TransitRouteDto(
        int id,
        String shortName,
        String longName,
        TransitMode mode,
        String color,
        String textColor,
        List<TransitPatternDto> patterns) {
}
