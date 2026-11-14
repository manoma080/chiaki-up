package com.gameblabla.chiaki.stream;

import java.util.HashSet;
import java.util.Set;

/** Tracks only devices identified by Android as gamepads or joysticks. */
final class ControllerConnectionTracker {
    private final Set<Integer> devices = new HashSet<>();
    private boolean disconnected;

    void add(int id) {
        devices.add(id);
        disconnected = false;
    }

    void remove(int id) {
        if (devices.remove(id) && devices.isEmpty()) disconnected = true;
    }

    boolean isDisconnected() { return disconnected; }
    void restoreDisconnected(boolean value) { disconnected = value && devices.isEmpty(); }
    Set<Integer> deviceIds() { return new HashSet<>(devices); }
}
