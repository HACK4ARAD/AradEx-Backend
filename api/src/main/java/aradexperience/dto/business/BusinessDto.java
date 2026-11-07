package aradexperience.dto.business;

/** docs/API.md: BusinessDto = BusinessSummary + { description, address, lat, lng, phone?, website?, viewsCount }. */
public record BusinessDto(Long id, String name, String category, boolean verified, String imageUrl, String description,
                          String address, Double lat, Double lng, String phone, String website, int viewsCount) {
}
