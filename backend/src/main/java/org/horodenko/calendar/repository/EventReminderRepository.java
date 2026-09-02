package org.horodenko.calendar.repository;

import org.horodenko.calendar.domain.EventReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventReminderRepository extends JpaRepository<EventReminder, UUID> {

    /** Lembretes de varias series de uma vez, para nao consultar um por ocorrencia. */
    @Query("""
            select r from EventReminder r
            where r.event.id in :eventIds
            order by r.minutesBefore
            """)
    List<EventReminder> findByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    /**
     * Todos os lembretes ligados, com a serie junto: e a partir deles que o disparador
     * descobre que series precisa expandir.
     */
    @Query("""
            select r from EventReminder r
            join fetch r.event
            where r.enabled = true
            """)
    List<EventReminder> findAllEnabledWithEvent();

    /**
     * Maior antecedencia ligada, ou {@code null} quando nao ha lembrete nenhum. Define
     * ate onde o disparador precisa olhar: nada alem disso pode estar vencido.
     */
    @Query("select max(r.minutesBefore) from EventReminder r where r.enabled = true")
    Integer findLargestEnabledLead();
}
