package aradexperience.dto.places;

/** Un punct de ghid virtual (E3/W8), în ordinea `position`. `durationSec` = cât durează citit cu voce tare. */
public record GuidePointDto(
        long id,
        int position,
        String title,
        String text,
        String imageUrl,
        String audioUrl,
        Double lat,
        Double lng,
        Integer durationSec) {
}
