package com.hamza.controlsfx.file.network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Enumeration;

public class MacAddress {

    private MacAddress() {
    }

    /**
     * Retrieves the MAC addresses of all network interfaces on the local machine.
     *
     * @return A StringBuilder containing the MAC addresses of all network interfaces formatted in hexadecimal.
     * @throws SocketException If an I/O error occurs.
     */
    public static StringBuilder getAllMacAddress() throws SocketException {
        StringBuilder stringBuilder = new StringBuilder();
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface ni = networkInterfaces.nextElement();
            byte[] hardwareAddress = ni.getHardwareAddress();
            if (hardwareAddress != null) {
                String[] hexadecimalFormat = new String[hardwareAddress.length];
                for (int i = 0; i < hardwareAddress.length; i++) {
                    hexadecimalFormat[i] = String.format("%02X", hardwareAddress[i]);
                }
                stringBuilder.append("#").append(LocalDateTime.now()).append("\n");
                stringBuilder.append(ni.getDisplayName()).append("\n");
                stringBuilder.append(ni.getName()).append(" : ").append(String.join("-", hexadecimalFormat)).append("\n");
                stringBuilder.append("-------------------------------- \n");
            }
        }
        return stringBuilder;
    }

    /**
     * Retrieves the MAC address of the local machine.
     *
     * @return the MAC address as a String
     * @throws UnknownHostException if the local host name could not be resolved into an address
     * @throws SocketException if an error occurs while accessing the network interface
     */
    public static String getActualMacAddress() throws UnknownHostException, SocketException {
        String macAddress = "";
        InetAddress inetAddress = InetAddress.getLocalHost();
        NetworkInterface network = NetworkInterface.getByInetAddress(inetAddress);
        byte[] macArray = network.getHardwareAddress();
        StringBuilder str = new StringBuilder();
        // Convert the macArray to String
        for (int i = 0; i < macArray.length; i++) {
            str.append(String.format("%02X%s", macArray[i], (i < macArray.length - 1) ? " " : ""));
            macAddress = str.toString();
        }
        return macAddress;
    }
}
