package org.example.gp.config;

/**
 * Кантори, освободени от проверка за абонамент.
 */
public final class SubscriptionConstants {

    private SubscriptionConstants() {}

    /** Фиксиран officeId за ДИАНА - КИРИЛОВИ ЕООД */
    public static final Long EXEMPT_OFFICE_ID = 1000000001L;

    /** ЕИК / БУЛСТАТ на освободената кантора */
    public static final String EXEMPT_EIK = "1781774289123";

    /** Официално ime на освободената кантора */
    public static final String EXEMPT_OFFICE_NAME = "ДИАНА - КИРИЛОВИ ЕООД";
}
