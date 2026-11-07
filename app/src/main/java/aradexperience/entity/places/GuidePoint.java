package aradexperience.entity.places;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tabelul `guide_points` (V3): punctele ghidului virtual; UNIQUE (place_id, position), ON DELETE CASCADE din `places`. */
@Entity
@Table(name = "guide_points")
@Getter
@Setter
@NoArgsConstructor
public class GuidePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    private Double lat;

    private Double lng;

    @Column(name = "duration_sec")
    private Integer durationSec;
}
