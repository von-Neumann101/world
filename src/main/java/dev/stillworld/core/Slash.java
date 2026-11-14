package dev.stillworld.core;

/** A fixed 4 x 3 x 0.3 block oriented box, centered at its stored world position. */
public record Slash(double x, double y, double z, float yaw, float pitch) {
    private static final double HALF_WIDTH = 2;
    private static final double HALF_HEIGHT = 1.5;
    private static final double HALF_THICKNESS = .15;

    /** Tests the target's current world AABB; touching faces and edges count as hits. */
    public boolean intersects(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        double sinPitch = Math.sin(pitchRadians);
        double cosPitch = Math.cos(pitchRadians);

        // Right, up (= normal cross right), normal. No world-up normalization
        // is needed, so this basis remains well-defined at pitch +/-90 degrees.
        double[][] basis = {
                {cosYaw, 0, sinYaw},
                {-sinYaw * sinPitch, cosPitch, cosYaw * sinPitch},
                {-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch}
        };
        double extentX = (maxX - minX) * .5;
        double extentY = (maxY - minY) * .5;
        double extentZ = (maxZ - minZ) * .5;
        double deltaX = minX + extentX - x;
        double deltaY = minY + extentY - y;
        double deltaZ = minZ + extentZ - z;

        // Separating-axis theorem: three world axes, three slash axes, and
        // all nine edge cross-products are required for exact box intersection.
        for (int i = 0; i < 3; i++) {
            if (separated(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0,
                    deltaX, deltaY, deltaZ, extentX, extentY, extentZ, basis)) {
                return false;
            }
        }
        for (double[] axis : basis) {
            double ax = axis[0];
            double ay = axis[1];
            double az = axis[2];
            if (separated(ax, ay, az, deltaX, deltaY, deltaZ, extentX, extentY, extentZ, basis)
                    || separated(0, az, -ay, deltaX, deltaY, deltaZ, extentX, extentY, extentZ, basis)
                    || separated(-az, 0, ax, deltaX, deltaY, deltaZ, extentX, extentY, extentZ, basis)
                    || separated(ay, -ax, 0, deltaX, deltaY, deltaZ, extentX, extentY, extentZ, basis)) {
                return false;
            }
        }
        return true;
    }

    private static boolean separated(double ax, double ay, double az,
                                     double dx, double dy, double dz,
                                     double ex, double ey, double ez, double[][] basis) {
        double distance = Math.abs(ax * dx + ay * dy + az * dz);
        double targetRadius = ex * Math.abs(ax) + ey * Math.abs(ay) + ez * Math.abs(az);
        double slashRadius = HALF_WIDTH * projection(ax, ay, az, basis[0])
                + HALF_HEIGHT * projection(ax, ay, az, basis[1])
                + HALF_THICKNESS * projection(ax, ay, az, basis[2]);
        // Scale the contact-roundoff allowance with the unnormalized axis.
        // A zero cross-product naturally cannot separate the boxes.
        double tolerance = 1e-10 * Math.sqrt(ax * ax + ay * ay + az * az);
        return distance > targetRadius + slashRadius + tolerance;
    }

    private static double projection(double ax, double ay, double az, double[] basisAxis) {
        return Math.abs(ax * basisAxis[0] + ay * basisAxis[1] + az * basisAxis[2]);
    }
}
