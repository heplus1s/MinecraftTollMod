package dev.jdguo.toll;

final class TollNearby {
    private static final double MAX_DISTANCE_SQUARED = 100.0;

    private TollNearby() {}

    record Selection(TollStore.Point point, boolean ambiguous) {}

    static Selection select(Iterable<TollStore.Point> points, String dimension, Geometry.Position position) {
        TollStore.Point best = null;
        boolean bestInside = false;
        boolean tied = false;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var point : points) {
            if (!dimension.equals(point.dimension)) continue;
            boolean inside = point.inside(position, 0);
            var center = point.center();
            double dx = center.x() - position.x();
            double dy = center.y() - position.y();
            double dz = center.z() - position.z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (!inside && distance > MAX_DISTANCE_SQUARED) continue;
            if (best == null || (inside && !bestInside) || (inside == bestInside && distance < bestDistance)) {
                best = point;
                bestInside = inside;
                bestDistance = distance;
                tied = false;
            } else if (inside == bestInside && distance == bestDistance) {
                tied = true;
            }
        }
        return new Selection(tied ? null : best, tied);
    }
}
