package com.joying.bluetoothsettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioSessionChecks {
    static final class Fake implements AudioSession.Port {
        final List<String> events = new ArrayList<>();
        final AudioSession session = new AudioSession(this);
        boolean grant = true;
        public boolean acquireFocus() { events.add("acquire"); return grant; }
        public void releaseFocus() { events.add("release"); }
        public void pauseBluetooth() { events.add("pause"); }
        public void error(String message) { events.add("error"); }
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        int cases = 0;
        Fake f = new Fake(); f.session.routeChanged(true, false);
        check(f.events.isEmpty(), "observing an existing route does not seize focus"); cases++;
        check(f.session.begin() && f.session.requested() && f.events.equals(Arrays.asList("acquire")), "explicit ON acquires focus"); cases++;
        f.session.begin(); check(f.events.size() == 1, "repeated ON does not reacquire focus"); cases++;
        f.session.routeChanged(false, true); check(f.session.requested(), "hold focus during source acquisition");
        f.session.routeChanged(true, false); check(f.events.size() == 1, "confirmed source needs no extra focus request"); cases++;
        f.session.stop(); check(f.events.equals(Arrays.asList("acquire", "pause", "release")) && !f.session.requested(), "OFF pauses before releasing focus"); cases++;
        f.session.routeChanged(false, false); check(f.events.size() == 3, "OFF confirmation cannot resume or repeat playback commands"); cases++;
        f = new Fake(); f.grant = false;
        check(!f.session.begin() && !f.session.requested() && f.events.equals(Arrays.asList("acquire", "error")), "denied focus prevents acquisition"); cases++;
        f = new Fake(); f.session.begin(); f.session.routeChanged(false, false);
        check(!f.session.requested() && f.events.equals(Arrays.asList("acquire", "pause", "release")), "source failure releases focus and pauses sender"); cases++;
        f = new Fake(); f.session.begin(); f.session.routeChanged(true, false); f.session.routeChanged(false, false);
        check(!f.session.requested() && f.events.equals(Arrays.asList("acquire", "pause", "release")), "external source change releases session"); cases++;
        f = new Fake(); f.session.begin(); f.session.close(); f.session.close();
        check(f.events.equals(Arrays.asList("acquire", "pause", "release", "release")), "close pauses once and never resumes a player"); cases++;
        f = new Fake(); f.session.stop(); check(f.events.equals(Arrays.asList("pause", "release")), "explicit OFF pauses even without prior ownership"); cases++;
        System.out.println(cases + " audio-session scenarios passed.");
    }
}
