package stillasleep.three;

/**
 * An axis-aligned box used for collision: walls, floors, furniture, platforms.
 *
 * <p>"AABB" is short for "axis-aligned bounding box". Axis-aligned means the
 * box is never rotated: its sides always line up with the x, y and z
 * directions. That makes it very cheap to test whether two boxes overlap,
 * because only the smallest and largest value on each axis have to be stored
 * and compared.
 *
 * <p>All values are in metres. x runs east, y runs up and z runs south.
 *
 * @param minX west edge
 * @param minY bottom
 * @param minZ north edge
 * @param maxX east edge
 * @param maxY top
 * @param maxZ south edge
 */
record Aabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    /**
     * Builds a box from its centre and half sizes.
     *
     * @param cx centre x
     * @param cy centre y
     * @param cz centre z
     * @param hx half width
     * @param hy half height
     * @param hz half depth
     * @return the box
     */
    static Aabb centered(float cx, float cy, float cz, float hx, float hy, float hz) {
        // Step half a size away from the centre in both directions on each axis.
        return new Aabb(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    /**
     * Tests against another box given by its bounds.
     *
     * @param x0 west edge of the other box
     * @param y0 bottom of the other box
     * @param z0 north edge of the other box
     * @param x1 east edge of the other box
     * @param y1 top of the other box
     * @param z1 south edge of the other box
     * @return true if the two boxes overlap (touching does not count)
     */
    boolean overlaps(float x0, float y0, float z0, float x1, float y1, float z1) {
        // A tiny tolerance (a tenth of a millimetre). Without it, a player
        // standing exactly on top of a floor, or pressed flat against a wall,
        // would count as "inside" it because of small rounding errors.
        float e = 1e-4f;

        // Two boxes overlap only if their ranges overlap on all three axes.
        // On each axis: the other box must start before this one ends, and
        // end after this one starts.
        return x0 < maxX - e && x1 > minX + e && y0 < maxY - e && y1 > minY + e
                && z0 < maxZ - e && z1 > minZ + e;
    }
}
