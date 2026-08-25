package org.horodenko.calendar.repository;

import org.horodenko.calendar.domain.EventOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventOverrideRepository extends JpaRepository<EventOverride, UUID> {

    /**
     * Ocorrencias remarcadas que caem dentro da janela.
     *
     * <p>Necessario porque uma ocorrencia pode ter sido movida de fora da janela para
     * dentro dela: expandir a regra na janela nunca encontraria essa ocorrencia, ja que
     * o horario original dela esta fora.
     */
    @Query("""
            select o from EventOverride o
            join fetch o.event
            where o.type = org.horodenko.calendar.domain.OverrideType.MODIFIED
              and o.startAt is not null
              and o.startAt < :windowEnd
              and o.endAt >= :windowStart
            """)
    List<EventOverride> findRescheduledIntoWindow(@Param("windowStart") LocalDateTime windowStart,
                                                  @Param("windowEnd") LocalDateTime windowEnd);
}
