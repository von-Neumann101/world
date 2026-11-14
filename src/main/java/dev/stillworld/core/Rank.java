package dev.stillworld.core;

public enum Rank {
    RANK1("1"), RANK2("2"), RANK3("3"), RANK4("4"), RANK4_5("4.5"), RANK5("5"), RANK6("6");
    private final String label;
    Rank(String label) { this.label = label; }
    public String label() { return label; }
    public boolean canPlace() { return ordinal() >= RANK2.ordinal(); }
    public boolean canBreak() { return ordinal() >= RANK3.ordinal(); }
    public boolean canMark() { return ordinal() >= RANK4.ordinal(); }
    public boolean canSeeLive() { return ordinal() >= RANK4_5.ordinal(); }
    public boolean defersDamage() { return ordinal() >= RANK5.ordinal(); }
    public boolean canSlash() { return this == RANK6; }
}
