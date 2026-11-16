package org.mcla.recomp;

public final class TouchStickTest {
    private static int checks;
    private static void check(boolean okay) {
        checks++;
        if (!okay) throw new AssertionError("stick check " + checks);
    }
    public static void main(String[] args) {
        MclaTouchStick stick = new MclaTouchStick(), other = new MclaTouchStick();
        check(!stick.active() && stick.x == 0 && stick.y == 0);
        check(stick.press(5));
        check(!stick.press(2)); // Another finger never steals the active stick.
        stick.move(5,100,100,100,100,80); check(stick.x == 0 && stick.y == 0);
        stick.move(5,105,100,100,100,80); check(stick.x == 0 && stick.y == 0);
        stick.move(5,180,100,100,100,80); check(stick.x == 32767 && stick.y == 0);
        stick.move(2,20,100,100,100,80); check(stick.x == 32767 && stick.y == 0);
        stick.move(5,100,20,100,100,80); check(stick.x == 0 && stick.y == 32767);
        stick.move(5,20,100,100,100,80); check(stick.x == -32767 && stick.y == 0);
        stick.move(5,100,180,100,100,80); check(stick.x == 0 && stick.y == -32767);
        for (int i=0;i<360;i++) {
            double angle=Math.toRadians(i);
            stick.move(5,(float)(100+1000*Math.cos(angle)),(float)(100-1000*Math.sin(angle)),100,100,80);
            check(Math.hypot(stick.x,stick.y)<=32768);
            check(Math.abs(Math.hypot(stick.x,stick.y)-32767)<=1);
        }
        stick.move(5,143.2f,100,100,100,80); check(Math.abs(stick.x-16384)<=1);
        stick.release(2); check(stick.active());
        check(other.press(2)); other.move(2,0,80,0,0,80);
        check(other.y == -32767 && stick.x > 0);
        stick.release(5); check(!stick.active() && stick.x == 0 && stick.y == 0);
        check(stick.press(7)); stick.move(7,100,100,100,100,80);
        check(stick.x == 0 && stick.y == 0);
        stick.move(7,Float.NaN,100,100,100,80); check(stick.x == 0 && stick.y == 0);
        stick.clear(); other.clear(); check(!stick.active() && !other.active());
        System.out.println("PASS "+checks+" joystick ownership, radial mapping, bounds and release checks");
    }
}
