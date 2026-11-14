package dev.stillworld;

import dev.stillworld.core.Slash;

/** Standalone geometry regression tests; no Minecraft or test framework required. */
public final class SlashTests {
    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        var flat = new Slash(0, 0, 0, 0, 0);
        hit(flat, -.1, -.1, -.1, .1, .1, .1, "center intersects");
        hit(flat, 2, -.1, -.1, 2.2, .1, .1, "width boundary touches");
        miss(flat, 2.0001, -.1, -.1, 2.2, .1, .1, "outside width separates");
        hit(flat, -2.2, -.1, -.1, -2, .1, .1, "negative width boundary touches");
        miss(flat, -2.2, -.1, -.1, -2.0001, .1, .1, "outside negative width separates");
        hit(flat, -.1, 1.5, -.1, .1, 1.7, .1, "height boundary touches");
        miss(flat, -.1, 1.5001, -.1, .1, 1.7, .1, "outside height separates");
        hit(flat, -.1, -1.7, -.1, .1, -1.5, .1, "negative height boundary touches");
        hit(flat, -.1, -.1, .15, .1, .1, .3, "thickness boundary touches");
        miss(flat, -.1, -.1, .1501, .1, .1, .3, "outside thickness separates");
        hit(flat, -.1, -.1, -.3, .1, .1, -.15, "negative thickness boundary touches");
        miss(flat, -.1, -.1, -.3, .1, .1, -.1501, "outside negative thickness separates");
        hit(flat, 2, 1.5, .15, 2, 1.5, .15, "zero-volume box touches corner");

        var moved = new Slash(125.5, -30.25, 400.75, 0, 0);
        hit(moved, 125.4, -30.35, 400.65, 125.6, -30.15, 400.85, "world translation preserves center hit");
        miss(moved, -.1, -.1, -.1, .1, .1, .1, "slash stays at its stored position");

        var quarterTurn = new Slash(0, 0, 0, 90, 0);
        hit(quarterTurn, -.05, -.05, 1.9, .05, .05, 2, "yaw rotates width into Z");
        miss(quarterTurn, .2, -.05, -.05, .3, .05, .05, "yaw rotates thickness into X");

        var diagonal = new Slash(0, 0, 0, 45, 0);
        hit(diagonal, .95, -.05, .95, 1.05, .05, 1.05, "diagonal plane intersects");
        miss(diagonal, 1.3, -.05, -1.4, 1.4, .05, -1.3, "diagonal bounding-box corner is not a hit");
        hit(diagonal, 1.4, -.05, 1.4, 1.5, .05, 1.5, "rotated width edge crosses target");
        miss(diagonal, 1.5, -.05, 1.5, 1.6, .05, 1.6, "rotated width edge separates");

        var tilted = new Slash(0, 0, 0, 0, 45);
        hit(tilted, -.05, .95, .95, .05, 1.05, 1.05, "pitch rotates vertical plane");
        miss(tilted, -.05, .95, -1.05, .05, 1.05, -.95, "pitched bounding-box corner is not a hit");
        // All six face-normal projections overlap; an edge cross-product separates these boxes.
        miss(new Slash(0, 0, 0, 45, 45), .9, -2.7, -3.5, 3.3, .3, -1.9,
                "edge cross-product axis excludes a false hit");

        var verticalLook = new Slash(0, 0, 0, 0, 90);
        hit(verticalLook, -.05, -.05, 1.35, .05, .05, 1.45, "pitch 90 keeps full height along Z");
        miss(verticalLook, -.05, .2, -.05, .05, .3, .05, "pitch 90 thickness is vertical");
        miss(verticalLook, -.05, -.05, 1.6, .05, .05, 1.7, "pitch 90 preserves height limit");
        hit(new Slash(0, 0, 0, 0, 89.999f), -.05, -.05, 1.35, .05, .05, 1.45,
                "near-vertical pitch remains stable");
        hit(new Slash(0, 0, 0, 0, -90), -.05, -.05, -1.45, .05, .05, -1.35,
                "negative vertical pitch remains stable");
        hit(new Slash(0, 0, 0, 137, 90), -.05, -.05, -.05, .05, .05, .05,
                "vertical pitch supports arbitrary yaw");

        hit(flat, -10, -10, -10, 10, 10, 10, "larger target encloses slash");
        hit(flat, -5, -.1, -.05, 5, .1, .05, "target center and extent determine crossing");
        hit(diagonal, -.2, -.1, .9, 1.2, .1, 1.1, "target crosses plane despite its center missing");
        miss(flat, -10, -10, .2, 10, 10, 5, "large target still separates on thickness");

        if (failures != 0) {
            throw new AssertionError("FAIL " + failures + " of " + checks + " slash geometry checks");
        }
        System.out.println("PASS " + checks + " slash geometry checks");
    }

    private static void hit(Slash slash, double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ, String message) {
        check(slash.intersects(minX, minY, minZ, maxX, maxY, maxZ), message);
    }

    private static void miss(Slash slash, double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ, String message) {
        check(!slash.intersects(minX, minY, minZ, maxX, maxY, maxZ), message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + message);
        }
    }
}
