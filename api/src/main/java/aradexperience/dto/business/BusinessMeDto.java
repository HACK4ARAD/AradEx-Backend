package aradexperience.dto.business;

/** GET `/business/me`: firma utilizatorului + statisticile din panou. */
public record BusinessMeDto(BusinessDto business, Stats stats) {

    /** `codesUsed` vine din T10 (coduri de ofertă folosite), `pointsPayments` din T11 (plăți cu puncte); până atunci 0. */
    public record Stats(int views, long codesUsed, long pointsPayments, long upcomingEvents) {
    }
}
