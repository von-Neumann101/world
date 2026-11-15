package dev.stillworld;

import dev.stillworld.core.DamageLedger;
import dev.stillworld.core.Rank;
import java.util.UUID;

public final class CoreTests {
    private static int checks;
    public static void main(String[] args) {
        check(!Rank.RANK1.canPlace() && Rank.RANK2.canPlace(), "placement requires rank2");
        check(!Rank.RANK2.canBreak() && Rank.RANK3.canBreak(), "breaking requires rank3");
        check(!Rank.RANK3.canMark() && Rank.RANK4.canMark(), "marking requires rank4");
        check(Rank.RANK4_5.canMark() && Rank.RANK4_5.canSeeLive() && !Rank.RANK4_5.defersDamage(), "rank4.5 adds live sight without deferred damage");
        check(Rank.RANK5.defersDamage() && !Rank.RANK5.canSlash() && Rank.RANK6.canSlash(), "slash permission remains separate");
        check(Rank.INFINITY.canPlace() && Rank.INFINITY.canBreak() && Rank.INFINITY.canMark()
                && Rank.INFINITY.canSeeLive() && Rank.INFINITY.defersDamage() && Rank.INFINITY.canSlash(), "infinity includes every rank6 skill");
        check(Rank.INFINITY.unlimitedPsyche() && !Rank.RANK6.unlimitedPsyche(), "infinity exemption does not change ordinary ranks");
        check(Rank.RANK6.ordinal()==6 && Rank.INFINITY.ordinal()==7, "new rank preserves existing wire identities");
        for(String name:new String[]{"infinity","INF","\u221e","\u267e","\u267e\ufe0f"})
            check(Rank.fromCommand(name).orElseThrow()==Rank.INFINITY,"infinity command spelling "+name);
        check(Rank.fromCommand("4.5").orElseThrow()==Rank.RANK4_5 && Rank.fromCommand("7").isEmpty(),"existing ranks and invalid input remain distinct");
        var ledger = new DamageLedger();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        ledger.add(a, 5); ledger.add(a, 7); ledger.add(b, 10);
        close(ledger.accumulated(a), 12, "base damage accumulates without 0.8 multiplier");
        close(ledger.drain(a), 19.2f, "exit multiplies total by 1.6");
        close(ledger.drain(a), 0, "exit cannot pay a target twice");
        close(ledger.drain(b), 16, "targets have separate balances");
        ledger.add(a, Float.NaN); ledger.add(a, Float.POSITIVE_INFINITY); ledger.add(a, -2);
        close(ledger.drain(a), 0, "invalid amounts cannot poison a balance");
        ledger.add(a, 5); ledger.discard(a);
        close(ledger.drain(a), 0, "dead targets lose pending damage");
        System.out.println("PASS " + checks + " core behavior checks");
        SlashTests.main(args);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.0001f, message + ": expected " + expected + ", got " + actual);
    }
}
