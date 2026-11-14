package com.gameblabla.chiaki.stream;

public class ControllerConnectionTrackerTest {
    public static void main(String[] args) {
        ControllerConnectionTracker tracker = new ControllerConnectionTracker();
        check(!tracker.isDisconnected());
        tracker.add(1);
        tracker.add(2);
        tracker.remove(99);
        check(!tracker.isDisconnected());
        tracker.remove(1);
        check(!tracker.isDisconnected());
        tracker.remove(2);
        check(tracker.isDisconnected());
        tracker.remove(2);
        check(tracker.isDisconnected());
        tracker.add(3);
        check(!tracker.isDisconnected());
        ControllerConnectionTracker restored = new ControllerConnectionTracker();
        for (int id : tracker.deviceIds()) restored.add(id);
        restored.remove(3);
        check(restored.isDisconnected());
        restored.restoreDisconnected(true);
        check(restored.isDisconnected());
        restored.add(4);
        check(!restored.isDisconnected());
        System.out.println("ControllerConnectionTracker behavioral tests passed");
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
