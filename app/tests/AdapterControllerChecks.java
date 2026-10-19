package com.joying.bluetoothsettings;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public final class AdapterControllerChecks {
    static final class Event implements Comparable<Event> {
        final long at; final Runnable task;
        Event(long at, Runnable task) { this.at = at; this.task = task; }
        public int compareTo(Event other) { return Long.compare(at, other.at); }
    }
    static final class Fake implements AdapterController.Port {
        final PriorityQueue<Event> queue = new PriorityQueue<>();
        final List<Boolean> requests = new ArrayList<>();
        final AdapterController control = new AdapterController(this);
        int state = AdapterController.OFF, errors, prompts;
        long now;
        boolean accepted = true, denied;
        public int state() { return state; }
        public boolean setEnabled(boolean on) {
            requests.add(on);
            if (denied) throw new SecurityException();
            return accepted;
        }
        public long now() { return now; }
        public void later(Runnable task, long delay) { queue.add(new Event(now + delay, task)); }
        public void changed() {}
        public void error(String message) { errors++; }
        public void enablePermissionRequired() { prompts++; }
        void advance(long delay) {
            long end = now + delay;
            while (!queue.isEmpty() && queue.peek().at <= end) {
                Event e = queue.remove(); now = e.at; e.task.run();
            }
            now = end;
        }
    }
    static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
    public static void main(String[] args) {
        int cases = 0;
        Fake f = new Fake(); f.control.request(true);
        check(f.requests.size() == 1 && f.requests.get(0) && f.prompts == 0 && f.control.busy(), "direct enable without prompt; wait for state"); cases++;
        f.state = AdapterController.ON; f.control.refresh(); f.advance(15000);
        check(!f.control.busy() && f.requests.size() == 1 && f.errors == 0, "confirm ON and cancel stale timers"); cases++;
        f = new Fake(); f.control.request(true); f.advance(2999);
        check(f.requests.size() == 1, "no premature retry");
        f.advance(1); check(f.requests.size() == 2, "one retry after stable OFF"); f.advance(10000);
        check(f.requests.size() == 2 && f.errors == 1 && !f.control.busy(), "bounded retry and timeout"); cases++;
        f = new Fake(); f.state = AdapterController.BLE_ON; f.control.request(true); f.advance(3000);
        check(f.requests.size() == 2, "BLE-only can be promoted and retried"); cases++;
        f = new Fake(); f.state = AdapterController.BLE_TURNING_OFF; f.control.request(true); f.advance(6000);
        check(f.requests.isEmpty(), "wait through BLE shutdown");
        f.state = AdapterController.BLE_ON; f.control.refresh();
        check(f.requests.size() == 1, "enable when shutdown reaches stable state"); cases++;
        f = new Fake(); f.state = AdapterController.TURNING_ON; f.control.request(true); f.advance(12000);
        check(f.requests.isEmpty() && f.errors == 1, "do not interrupt an in-progress transition"); cases++;
        f = new Fake(); f.state = AdapterController.ON; f.control.request(false);
        check(f.requests.size() == 1 && !f.requests.get(0) && f.control.busy(), "off waits for actual shutdown");
        f.state = AdapterController.BLE_TURNING_OFF; f.control.refresh(); check(f.control.busy(), "BLE shutdown not finished");
        f.state = AdapterController.BLE_ON; f.control.refresh(); check(!f.control.busy(), "BLE-only means classic audio is off"); cases++;
        f = new Fake(); f.accepted = false; f.control.request(true);
        check(!f.control.busy() && f.errors == 1 && f.prompts == 0, "reject does not silently claim success or repeat prompts"); cases++;
        f = new Fake(); f.denied = true; f.control.request(true); f.advance(15000);
        check(f.prompts == 1 && f.requests.size() == 1 && !f.control.busy(), "permission fallback only on denial"); cases++;
        f = new Fake(); f.state = AdapterController.ON; f.denied = true; f.control.request(false);
        check(f.prompts == 0 && f.errors == 1, "off denial does not open enable dialog"); cases++;
        f = new Fake(); f.control.request(true); f.control.cancel(); f.advance(15000);
        check(f.requests.size() == 1 && f.errors == 0, "close cancels retry"); cases++;
        f = new Fake(); f.state = AdapterController.ON; f.control.request(true);
        check(f.requests.isEmpty() && !f.control.busy(), "already ON needs no request"); cases++;
        f = new Fake(); f.state = -1; f.control.request(true);
        check(f.requests.isEmpty() && f.errors == 1 && !f.control.busy(), "unknown state cannot cause writes"); cases++;
        System.out.println(cases + " adapter-state scenarios passed.");
    }
}
