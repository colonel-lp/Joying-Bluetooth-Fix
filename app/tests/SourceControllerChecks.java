package com.joying.bluetoothsettings;

import java.util.ArrayList;
import java.util.List;

public final class SourceControllerChecks {
    static final class Fake implements SourceController.Port {
        final List<Integer> writes = new ArrayList<>();
        final List<Runnable> timers = new ArrayList<>();
        int saved = -1, errors;
        boolean accept = true;
        SourceController control;
        Fake(int previous) { saved = previous; control = new SourceController(this, previous); }
        public boolean select(int value) { writes.add(value); return accept; }
        public void savePrevious(int value) { saved = value; }
        public void later(Runnable task, long delay) { timers.add(task); }
        public void changed() {}
        public void error(String message) { errors++; }
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        int cases = 0;
        Fake f = new Fake(-1);
        f.control.toggle(); check(f.writes.isEmpty(), "unknown source must not be overwritten"); cases++;
        f.control.update(0); f.control.toggle();
        check(f.saved == 0 && f.control.busy() && !f.control.active(), "dispatch must not claim readback success");
        f.control.update(3); check(f.control.active() && !f.control.busy(), "source callback confirms acquisition"); cases++;
        f.control.toggle(); check(f.writes.get(1) == 0 && f.control.busy(), "off requests original source");
        f.control.update(0); check(f.saved == -1 && !f.control.active(), "restore clears ownership"); cases++;

        f = new Fake(-1); f.control.update(5); f.control.toggle();
        final int[] closed = {0};
        f.control.releaseOwned(() -> closed[0]++);
        check(closed[0] == 0 && f.writes.size() == 1, "close waits for acquisition");
        f.control.update(3); check(f.writes.size() == 2 && f.writes.get(1) == 5 && closed[0] == 0, "close restores after acquisition");
        f.control.update(5); check(closed[0] == 1, "close completes after restore readback"); cases++;

        f = new Fake(0); f.control.update(3); f.control.update(8); f.control.releaseOwned(() -> {});
        check(f.writes.isEmpty() && f.saved == -1, "external source selection must not be undone"); cases++;
        f = new Fake(5); f.control.update(3); f.control.releaseOwned(() -> {});
        check(f.writes.get(0) == 5, "saved owner survives process restart"); cases++;
        f = new Fake(-1); f.control.update(3); f.control.releaseOwned(() -> {});
        check(f.writes.isEmpty(), "close does not seize a source selected outside this app");
        f.control.toggle(); check(f.writes.get(0) == 0, "explicit source off falls back to Android"); cases++;
        f = new Fake(-1); f.control.update(3); f.control.releaseActive(() -> {});
        check(f.writes.get(0) == 0, "adapter off releases existing Bluetooth route"); cases++;

        f = new Fake(-1); f.control.update(0); f.control.toggle();
        Runnable stale = f.timers.get(0); f.control.update(3); f.control.toggle(); stale.run();
        check(f.control.busy() && f.errors == 0, "stale timeout cannot cancel a later request"); cases++;
        f = new Fake(0); f.control.update(3); f.control.releaseOwned(() -> closed[0]++);
        int oldClosed = closed[0]; f.timers.get(0).run();
        check(closed[0] == oldClosed + 1 && f.saved == 0 && f.errors == 1, "restore timeout closes but keeps recovery state"); cases++;
        f = new Fake(-1); f.control.update(0); f.accept = false; f.control.toggle();
        check(!f.control.busy() && !f.control.active() && f.errors == 1, "rejected dispatch does not activate route"); cases++;
        f = new Fake(0); f.control.update(3);
        final int[] completions = {0};
        f.control.releaseOwned(() -> completions[0]++); f.control.releaseOwned(() -> completions[0]++);
        f.control.update(0); check(completions[0] == 2 && f.writes.size() == 1, "close and adapter-off share a pending release"); cases++;
        f = new Fake(5); f.control.disconnected(); f.control.releaseOwned(() -> completions[0]++);
        check(f.writes.isEmpty() && f.saved == 5 && f.errors == 1, "disconnect retains return source and reports incomplete restore"); cases++;
        f = new Fake(-1); f.control.update(0); f.control.toggle(); f.timers.get(0).run(); f.control.update(3);
        f.control.releaseOwned(() -> {}); check(f.writes.get(1) == 0, "late acquisition still gets released on close"); cases++;
        System.out.println(cases + " source-routing scenarios passed.");
    }
}
