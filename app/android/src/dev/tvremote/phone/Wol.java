package dev.tvremote.phone;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wake-on-LAN. The magic packet is a broadcast, so it only reaches the TV when the phone is on the TV's own network
 * (not through Tailscale or mobile data).
 */
final class Wol {
    /** Returns how many packets went out. lan: the TV's home IP, or "" if unknown. */
    static int send(String mac, String lan) {
        String hex = mac.replace(":", "").replace("-", "");
        if (hex.length() != 12) return 0;
        byte[] pkt = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) pkt[i] = (byte) 0xff;
        for (int i = 0; i < 16 * 6; i++) pkt[6 + i] = (byte) Integer.parseInt(hex.substring(i % 6 * 2, i % 6 * 2 + 2), 16);
        Set<InetAddress> to = new LinkedHashSet<>();
        try {
            to.add(InetAddress.getByName("255.255.255.255"));
            if (lan.lastIndexOf('.') > 0) to.add(InetAddress.getByName(lan.substring(0, lan.lastIndexOf('.')) + ".255"));
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                if (ni.isUp() && !ni.isLoopback())
                    for (InterfaceAddress a : ni.getInterfaceAddresses()) if (a.getBroadcast() != null) to.add(a.getBroadcast());
        } catch (Exception ignored) { }
        int sent = 0;
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            for (int round = 0; round < 3; round++)
                for (InetAddress ip : to) for (int port : new int[]{9, 7}) {
                    try { s.send(new DatagramPacket(pkt, pkt.length, ip, port)); sent++; } catch (Exception ignored) { }   // e.g. no route on mobile data
                }
        } catch (Exception ignored) { }
        return sent;
    }

    /** True when one of the phone's own networks is the TV's home network (same first three numbers). */
    static boolean sameNetwork(String lan) {
        if (lan.lastIndexOf('.') <= 0) return false;
        String net = lan.substring(0, lan.lastIndexOf('.') + 1);
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                if (ni.isUp() && !ni.isLoopback())
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) if (a.getHostAddress().startsWith(net)) return true;
        } catch (Exception ignored) { }
        return false;
    }
}
