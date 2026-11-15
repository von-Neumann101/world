package dev.stillworld.core;

public enum Rank {
    RANK1("1"), RANK2("2"), RANK3("3"), RANK4("4"), RANK4_5("4.5"), RANK5("5"), RANK6("6"), INFINITY("\u221e");
    private final String label;
    Rank(String label) { this.label = label; }
    public String label() { return label; }
    public String commandName() { return this == INFINITY ? "infinity" : label; }
    public String charmId() { return this == INFINITY ? "rank_infinity_charm" : "rank" + label.replace('.', '_') + "_charm"; }
    public static java.util.Optional<Rank> fromCommand(String value) {
        String name=value.strip();
        if(name.equalsIgnoreCase("inf")||name.equals("\u267e")||name.equals("\u267e\ufe0f"))return java.util.Optional.of(INFINITY);
        return java.util.Arrays.stream(values()).filter(r->r.label.equals(name)||r.commandName().equalsIgnoreCase(name)).findFirst();
    }
    public boolean unlimitedPsyche() { return this == INFINITY; }
    public boolean canPlace() { return ordinal() >= RANK2.ordinal(); }
    public boolean canBreak() { return ordinal() >= RANK3.ordinal(); }
    public boolean canMark() { return ordinal() >= RANK4.ordinal(); }
    public boolean canSeeLive() { return ordinal() >= RANK4_5.ordinal(); }
    public boolean defersDamage() { return ordinal() >= RANK5.ordinal(); }
    public boolean canSlash() { return ordinal() >= RANK6.ordinal(); }
}
