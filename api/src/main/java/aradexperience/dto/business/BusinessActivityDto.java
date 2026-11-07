package aradexperience.dto.business;

import aradexperience.enums.BusinessActivityType;

import java.time.OffsetDateTime;
import java.util.List;

/** GET `/business/activity`: ultimele 20 de întâmplări (cod folosit, ofertă salvată/revendicată, bilet, plată cu puncte). */
public record BusinessActivityDto(List<Item> items) {

    public record Item(BusinessActivityType type, String text, OffsetDateTime at) {
    }
}
