package org.example.schoolshop.common;

public final class OrderStatusUtil {

    private OrderStatusUtil() {
    }

    public static String toFrontend(int status) {
        return switch (status) {
            case 0 -> "pending";
            case 1 -> "completed";
            case 2 -> "cancelled";
            default -> "unknown";
        };
    }

    public static String withdrawToFrontend(int status) {
        return switch (status) {
            case 0 -> "pending";
            case 1 -> "completed";
            case 2 -> "rejected";
            default -> "unknown";
        };
    }
}
