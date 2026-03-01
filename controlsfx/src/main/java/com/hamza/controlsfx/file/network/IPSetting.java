package com.hamza.controlsfx.file.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;

public class IPSetting {

    private IPSetting() {
    }

    /**
     * Retrieves the local IP address by connecting to a known external IP address using Google's DNS service.
     *
     * @return the local IP address as a String
     * @throws Exception if the local IP address cannot be determined
     */
    public static String realIpByDnsGoogle() throws Exception {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            throw new Exception("No IP");
        }
    }

    /**
     * Fetches the public IP address of the local machine using Amazon's checkip service.
     *
     * @return The public IP address as a String, or "Cannot Execute Properly" if the request fails.
     */
    public static String realIpByAmazon() {
        String systemipaddress;
        try {
            URL url_name = new URL("http://checkip.amazonaws.com/");
            BufferedReader sc = new BufferedReader(new InputStreamReader(url_name.openStream()));
            systemipaddress = sc.readLine().trim();
        } catch (Exception e) {
            systemipaddress = "Cannot Execute Properly";
        }
        return systemipaddress;

    }

    /**
     * Retrieves the IP addresses of all network interfaces available on the system.
     *
     * @return A list of strings, each representing an IP address of a network interface.
     * @throws SocketException If an I/O error occurs.
     */
    public static ArrayList<String> getAllDataOfNetwork() throws SocketException {
        ArrayList<String> strings = new ArrayList<>();
        Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();
        while (interfaceEnumeration.hasMoreElements()) {
            NetworkInterface networkInterface = interfaceEnumeration.nextElement();
            Enumeration<InetAddress> addressEnumeration = networkInterface.getInetAddresses();
            while (addressEnumeration.hasMoreElements()) {
                InetAddress inetAddress = addressEnumeration.nextElement();
                strings.add(inetAddress.getHostAddress());
            }
        }
        return strings;
    }

}
