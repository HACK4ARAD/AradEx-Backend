package aradexperience.entity.transit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tabelul `transit_stops` (V12): o stație CTP (un peron; stațiile cu același nume pot avea id-uri diferite). Doar citire. */
@Entity
@Table(name = "transit_stops")
@Getter
@Setter
@NoArgsConstructor
public class TransitStop {

    @Id
    private Integer id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String code;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;
}
