package aradexperience.entity.transit;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.ListIndexBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabelul `transit_patterns` (V12): o variantă de traseu a unei linii, cu opririle ordonate din `transit_pattern_stops`
 * (`seq` de la 1). `path` = polilinie codificată (precizie 5), decodată în `TransitMapper`. Doar citire.
 */
@Entity
@Table(name = "transit_patterns")
@Getter
@Setter
@NoArgsConstructor
public class TransitPattern {

    @Id
    private Integer id;

    @Column(name = "route_id", nullable = false)
    private Integer routeId;

    @Column(name = "pattern_index", nullable = false)
    private Integer patternIndex;

    @Column(name = "from_stop_id")
    private Integer fromStopId;

    @Column(name = "to_stop_id")
    private Integer toStopId;

    @Column(nullable = false, columnDefinition = "text")
    private String path;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "transit_pattern_stops", joinColumns = @JoinColumn(name = "pattern_id"))
    @OrderColumn(name = "seq")
    @ListIndexBase(1)
    @Column(name = "stop_id", nullable = false)
    private List<Integer> stopIds = new ArrayList<>();
}
