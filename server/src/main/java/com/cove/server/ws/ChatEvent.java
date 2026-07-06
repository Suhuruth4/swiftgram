package com.cove.server.ws;

public record ChatEvent(String type, Object payload) {
    public static ChatEvent of(String type, Object payload) {
        return new ChatEvent(type, payload);
    }
}
