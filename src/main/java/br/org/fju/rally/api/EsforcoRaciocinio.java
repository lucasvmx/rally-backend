package br.org.fju.rally.api;

import java.util.Locale;

public enum EsforcoRaciocinio {
    NONE,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH;

    public String valorOpenRouter() {
        return name().toLowerCase(Locale.ROOT);
    }
}
