package aradexperience.entity.transit;

import aradexperience.enums.TransitMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tabelul `transit_routes` (V12): o linie CTP. `id` = routeId din Telelink, `shortName` = numărul afișat. Doar citire. */
@Entity
@Table(name = "transit_routes")
@Getter
@Setter
@NoArgsConstructor
public class TransitRoute {

    @Id
    private Integer id;

    @Column(name = "short_name", nullable = false, length = 20)
    private String shortName;

    @Column(name = "long_name", nullable = false, length = 200)
    private String longName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransitMode mode;

    @Column(length = 7)
    private String color;

    @Column(name = "text_color", length = 7)
    private String textColor;
}
