package com.joying.bluetoothsettings;
import java.util.ArrayList;
public final class OverlayPresenceChecks {
    static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        OverlayPresence state=new OverlayPresence();ArrayList<Boolean> a=new ArrayList<>(),b=new ArrayList<>();
        OverlayPresence.Listener first=a::add,second=b::add;
        state.add(first);check(a.size()==1&&!a.get(0),"Initial query is OFF and creates no overlay");
        state.set(true);state.add(second);check(a.get(1)&&b.get(0),"New and existing clients see the attached window");
        state.set(true);check(a.size()==2&&b.size()==1,"Reopening reuses presence without duplicate events");
        state.remove(first);state.set(false);check(a.size()==2&&b.size()==2&&!b.get(1),"Close updates subscribed clients only");
        state.add(first);check(!a.get(2),"Reconnect queries the current closed state");
        OverlayPresence reset=new OverlayPresence();check(!reset.visible(),"New process cannot inherit stale ON state");
        OverlayPresence.Listener[] self={null};self[0]=value->{if(value)reset.remove(self[0]);};reset.add(self[0]);reset.set(true);reset.set(false);
        check(!reset.visible(),"Client removal during notification is safe");
        System.out.println(checks+" overlay presence checks passed");
    }
}
