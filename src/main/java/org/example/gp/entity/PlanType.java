package org.example.gp.entity;

public enum PlanType {

    /** До 10 фирми, до 2 служители */
    BASIC(10, 2, "prod_UPeZSslPmIxgNj"),

    /** До 50 фирми, до 10 служители */
    PRO(50, 10, "prod_UPeZk5z8unKJWV");

    private final int maxCompanies;
    private final int maxStaff;
    private final String stripePriceId;

    PlanType(int maxCompanies, int maxStaff, String stripePriceId) {
        this.maxCompanies = maxCompanies;
        this.maxStaff = maxStaff;
        this.stripePriceId = stripePriceId;
    }

    public int getMaxCompanies() { return maxCompanies; }
    public int getMaxStaff() { return maxStaff; }
    public String getStripePriceId() { return stripePriceId; }
}
