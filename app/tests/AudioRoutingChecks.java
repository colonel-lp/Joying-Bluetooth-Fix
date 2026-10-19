package com.joying.bluetoothsettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercise focus ownership against real source transitions. */
public final class AudioRoutingChecks {
    static final class Rig {
        final List<String> events = new ArrayList<>();
        final List<Runnable> timers = new ArrayList<>();
        boolean grant = true, accept = true;
        AudioSession session;
        final SourceController source = new SourceController(new SourceController.Port() {
            public boolean select(int value) { events.add("source:" + value); return accept; }
            public void savePrevious(int value) {}
            public void later(Runnable task, long delay) { timers.add(task); }
            public void changed() { if (session != null) session.routeChanged(source.active(), source.busy()); }
            public void error(String message) { events.add("source-error"); }
        }, -1);
        Rig(int initial) {
            session = new AudioSession(new AudioSession.Port() {
                public boolean acquireFocus() { events.add("focus"); return grant; }
                public void releaseFocus() { events.add("release"); }
                public void pauseBluetooth() { events.add("pause"); }
                public void error(String message) { events.add("focus-error"); }
            });
            source.update(initial);
        }
        void on() { if (session.begin()) source.toggle(); }
        void off() { session.stop(); source.toggle(); }
        void lost() { session.stop(); source.releaseActive(() -> events.add("restored")); }
        void close() { session.close(); source.releaseOwned(() -> events.add("closed")); }
    }
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        int cases = 0;
        Rig r = new Rig(5); r.on(); r.source.update(3); r.off(); r.source.update(5);
        check(r.events.equals(Arrays.asList("focus", "source:3", "pause", "release", "source:5")), "normal ON/OFF pauses before abandon and restores without Play"); cases++;
        r = new Rig(0); r.on(); r.lost();
        check(r.source.busy() && !r.session.requested(), "focus loss releases immediately while source acquisition is pending");
        r.source.update(3); r.source.update(0);
        check(r.events.equals(Arrays.asList("focus", "source:3", "pause", "release", "source:0", "restored")), "pending focus loss restores after readback without reacquiring"); cases++;
        r = new Rig(5); r.on(); r.close(); r.source.update(3); r.source.update(5);
        check(r.events.equals(Arrays.asList("focus", "source:3", "pause", "release", "source:5", "closed")), "close during acquisition restores and never resumes"); cases++;
        r = new Rig(0); r.grant = false; r.on();
        check(r.events.equals(Arrays.asList("focus", "focus-error")) && !r.source.busy(), "focus denial cannot dispatch source acquisition"); cases++;
        r = new Rig(0); r.accept = false; r.on();
        check(!r.session.requested() && r.events.equals(Arrays.asList("focus", "source:3", "source-error", "pause", "release")), "source dispatch failure releases focus"); cases++;
        r = new Rig(0); r.on(); r.timers.get(0).run();
        check(!r.session.requested() && r.events.equals(Arrays.asList("focus", "source:3", "source-error", "pause", "release")), "source acquisition timeout pauses and releases"); cases++;
        r = new Rig(5); r.on(); r.source.update(3); r.source.update(8); r.close();
        check(r.events.equals(Arrays.asList("focus", "source:3", "pause", "release", "release", "closed")), "external source change is preserved on close"); cases++;
        r = new Rig(3); r.off(); r.source.update(0);
        check(r.events.equals(Arrays.asList("pause", "release", "source:0")), "explicit OFF on an existing route pauses without claiming focus"); cases++;
        r = new Rig(3); check(r.session.begin(), "explicit Play can take focus on an existing route"); r.lost(); r.source.update(0);
        check(r.events.equals(Arrays.asList("focus", "pause", "release", "source:0", "restored")), "focus loss releases existing route with Android fallback"); cases++;
        System.out.println(cases + " source/focus integration scenarios passed.");
    }
}
