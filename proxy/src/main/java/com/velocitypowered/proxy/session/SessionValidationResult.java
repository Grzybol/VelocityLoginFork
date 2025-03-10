package com.velocitypowered.proxy.session;

public class SessionValidationResult {
    private final String id;   // np. surowy Online-Mode UUID bez kresek
    private final String name; // nazwa weryfikowana przez Mojang

    public SessionValidationResult(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
