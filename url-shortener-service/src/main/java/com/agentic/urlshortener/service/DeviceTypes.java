package com.agentic.urlshortener.service;

import java.util.Locale;

/**
 * Very small user-agent classifier. Deliberately heuristic - the goal is a
 * useful analytics bucket, not exhaustive device detection.
 */
public final class DeviceTypes {

    public static final String UNKNOWN = "UNKNOWN";

    private DeviceTypes() {
    }

    public static String detect(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                || ua.contains("curl") || ua.contains("wget") || ua.contains("httpie")
                || ua.contains("postman") || ua.contains("java/") || ua.contains("okhttp")) {
            return "BOT_OR_CLIENT";
        }
        if (ua.contains("ipad") || ua.contains("tablet") || ua.contains("kindle")) {
            return "TABLET";
        }
        if (ua.contains("mobi") || ua.contains("iphone") || ua.contains("android")
                || ua.contains("windows phone")) {
            return "MOBILE";
        }
        if (ua.contains("mozilla") || ua.contains("chrome") || ua.contains("safari") || ua.contains("edg")) {
            return "DESKTOP";
        }
        return UNKNOWN;
    }
}
