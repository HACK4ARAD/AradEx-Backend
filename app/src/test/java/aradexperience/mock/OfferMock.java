package aradexperience.mock;

import aradexperience.dto.offers.OfferCreate;
import aradexperience.entity.business.Business;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
import aradexperience.enums.RedemptionStatus;
import aradexperience.enums.Role;
import aradexperience.security.CurrentUser;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Date de test pentru ofertele și codurile din T10: oferta „cafea -20%” a Cafenelei din Pasaj (firma 1), un localnic. */
public final class OfferMock {

    public static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Bucharest"));

    private OfferMock() {}

    public static Offer coffee() {
        Offer o = new Offer();
        o.setId(1L);
        o.setBusinessId(1L);
        o.setTitle("20% reducere la orice cafea");
        o.setDescription("Arată codul la casă.");
        o.setDiscountLabel("-20%");
        o.setCategory(OfferCategory.CAFE);
        o.setValidFrom(TODAY.minusDays(6));
        o.setValidUntil(TODAY.plusDays(20));
        o.setPointsPrice(30);
        o.setStatus(ContentStatus.PUBLISHED);
        o.setViewsCount(1240);
        o.setRedemptionsCount(95);
        o.setUsedCount(87);
        return o;
    }

    public static Offer offer(long id, long businessId, OfferCategory category, Integer pointsPrice, boolean local) {
        Offer o = coffee();
        o.setId(id);
        o.setBusinessId(businessId);
        o.setCategory(category);
        o.setPointsPrice(pointsPrice);
        o.setLocalProduct(local);
        o.setTitle("Oferta " + id);
        return o;
    }

    public static Business crama() {
        Business b = BusinessMock.crama();
        b.setLat(46.1340);
        b.setLng(21.6180);
        return b;
    }

    public static OfferRedemption issued(long id, String code) {
        OfferRedemption r = new OfferRedemption();
        r.setId(id);
        r.setOfferId(1L);
        r.setUserId(2L);
        r.setCode(code);
        r.setStatus(RedemptionStatus.ISSUED);
        r.setIssuedAt(OffsetDateTime.parse("2026-11-07T08:20:00+02:00"));
        return r;
    }

    public static OfferRedemption used(long id, String code) {
        OfferRedemption r = issued(id, code);
        r.setStatus(RedemptionStatus.USED);
        r.setUsedAt(OffsetDateTime.parse("2026-11-07T09:12:00+02:00"));
        r.setUsedByUserId(BusinessMock.OWNER_ID);
        return r;
    }

    public static CurrentUser local() {
        return new CurrentUser(2L, "local@demo.ro", Role.LOCAL);
    }

    public static OfferCreate create(LocalDate from, LocalDate until, ContentStatus status) {
        return new OfferCreate(" Meniu de Crăciun -15% ", "Doar în decembrie.", "-15%", null, false, from, until, null, null, status);
    }
}
