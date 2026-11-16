package org.mcla.recomp;

import java.util.HashSet;

public final class CheatsTest {
    public static void main(String[] args) {
        if (!"0".equals(MclaCheats.value(false)) || !"1".equals(MclaCheats.value(true)))
            throw new AssertionError("native boolean contract");
        if (MclaCheats.KEYS.length != 3 || MclaCheats.LABELS.length != 3 ||
                MclaCheats.ENVIRONMENT.length != 3)
            throw new AssertionError("three supported cheats only");
        HashSet<String> keys = new HashSet<>(), env = new HashSet<>();
        for (int i = 0; i < MclaCheats.KEYS.length; i++) {
            if (!keys.add(MclaCheats.KEYS[i]) || !env.add(MclaCheats.ENVIRONMENT[i]))
                throw new AssertionError("duplicate request");
            if (MclaCheats.KEYS[i].contains("money") || MclaCheats.KEYS[i].contains("unlock"))
                throw new AssertionError("progression edits not allowed");
            System.out.println("CHEAT " + MclaCheats.ENVIRONMENT[i]);
        }
        System.out.println("PASS Java cheat request mapping and native boolean contract");
    }
}
