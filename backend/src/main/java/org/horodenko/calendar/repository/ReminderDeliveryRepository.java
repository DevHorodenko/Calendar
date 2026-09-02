package org.horodenko.calendar.repository;

import org.horodenko.calendar.domain.NotificationChannelType;
import org.horodenko.calendar.domain.ReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, UUID> {

    @Query("""
            select d from ReminderDelivery d
            where d.reminder.id = :reminderId
              and d.occurrenceStart = :occurrenceStart
              and d.channel = :channel
            """)
    Optional<ReminderDelivery> find(@Param("reminderId") UUID reminderId,
                                    @Param("occurrenceStart") LocalDateTime occurrenceStart,
                                    @Param("channel") NotificationChannelType channel);

    /** Poda o historico: ele so existe para nao repetir envio, e envio velho nao repete. */
    @Modifying
    @Query("delete from ReminderDelivery d where d.attemptedAt < :before")
    int deleteAttemptedBefore(@Param("before") Instant before);
}
