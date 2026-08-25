package org.horodenko.calendar.repository;

import org.horodenko.calendar.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Eventos unicos que cruzam a janela. Um evento de duracao zero conta como
     * dentro da janela quando comeca nela, caso contrario ele nunca apareceria.
     */
    @Query("""
            select e from Event e
            where e.recurrenceRule is null
              and e.startAt < :windowEnd
              and (e.endAt > :windowStart or e.endAt = e.startAt)
              and e.endAt >= :windowStart
            order by e.startAt
            """)
    List<Event> findSingleEventsInWindow(@Param("windowStart") LocalDateTime windowStart,
                                         @Param("windowEnd") LocalDateTime windowEnd);

    /**
     * Series recorrentes que ja tinham comecado antes do fim da janela. Se a serie
     * terminou antes dela, a expansao simplesmente nao devolve nenhuma ocorrencia.
     */
    @Query("""
            select distinct e from Event e
            left join fetch e.overrides
            where e.recurrenceRule is not null
              and e.startAt < :windowEnd
            """)
    List<Event> findRecurringSeriesStartedBefore(@Param("windowEnd") LocalDateTime windowEnd);

    @Query("""
            select distinct e from Event e
            left join fetch e.overrides
            where e.id = :id
            """)
    Optional<Event> findByIdWithOverrides(@Param("id") UUID id);

    @Query("select e from Event e order by e.startAt desc")
    List<Event> findAllSeries();
}
