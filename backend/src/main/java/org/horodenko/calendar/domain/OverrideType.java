package org.horodenko.calendar.domain;

/** O que foi feito com uma ocorrencia especifica de uma serie recorrente. */
public enum OverrideType {

    /** A ocorrencia foi apagada e nao deve aparecer no calendario. */
    CANCELLED,

    /** A ocorrencia continua existindo, mas com dados proprios que substituem os da serie. */
    MODIFIED
}
