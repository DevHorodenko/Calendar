package org.horodenko.calendar.web.dto;

/** Alcance de uma edicao ou exclusao dentro de uma serie recorrente. */
public enum EditScope {

    /** So a ocorrencia apontada por occurrenceStart. */
    THIS,

    /** A ocorrencia apontada e todas as seguintes; as anteriores ficam intactas. */
    THIS_AND_FUTURE,

    /** A serie inteira, do inicio ao fim. */
    ALL
}
