package com.gdm.cloud.secureredis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

class SSHTunnel {
	private JSch jsch;
	private Session session;

	public JSch getJsch() {
		return jsch;
	}

	public void setJsch(JSch jsch) {
		this.jsch = jsch;
	}

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

}

public class SSHTunnelManager {

	final private int remoteSSHPort = 22;
	final private int remotePort = 8888;

	final private int localPortToFoward = 6379; // for eg:redis

	private Map<String, SSHTunnel> sshTunnelMap = new HashMap<>();

	public void checkAndUpdate() throws InterruptedException {

		while (true) {
			Thread.sleep(30000);
			checkAndUpdateTunnels();
		}

	}

	private void checkAndUpdateTunnels() {

		List<String> deviceAddresses = getDeviceAddress();
		String[] deviceAddressArray = deviceAddresses.toArray(new String[0]);

		try {
			List<HostTarget> pingResult = BulkPingUtil
					.getRechabilityStatus(deviceAddressArray);
			Map<String, HostTarget> pingResultMap = new HashMap<>();
			for (HostTarget hostTarget : pingResult) {
				pingResultMap.put(hostTarget.getIpAddress(), hostTarget);
			}

			removeUnManagedAddresses(pingResultMap);

			for (Map.Entry<String, HostTarget> hostTarget : pingResultMap
					.entrySet()) {

				String address = hostTarget.getKey();
				HostTarget status = hostTarget.getValue();

				if (status.isReachable) {
					SSHTunnel tunnel = sshTunnelMap.get(address);
					if (tunnel != null) {
						if (!tunnel.getSession().isConnected()) {
							System.out
									.println("Tunnel exists but not connected. Connecting Again : "
											+ address);
							try {
								tunnel.getSession().connect();
							} catch (Exception e) {
								System.out
										.println("Error connecting so re-establishing "
												+ address);
								tunnel = establishTunnel(address);
								sshTunnelMap.put(address, tunnel);
							}
						} else {
							// System.out.print
						}
					} else {
						tunnel = establishTunnel(address);
						sshTunnelMap.put(address, tunnel);
					}

				} else {
					// unreachable

					if (sshTunnelMap.containsKey(address)) {
						System.out
								.println("Un reachable device but not removing from map"
										+ address);
						// sshTunnelMap.remove(address); // Not sure if we need
						// to clean-up
					}
				}

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private SSHTunnel establishTunnel(String address) {

		JSch jsch = new JSch();

		try {
			System.out.println("Establishing Tunnel  " + address);
			Session session = jsch.getSession("admin", address, remoteSSHPort);
			jsch.addIdentity("/var/versa/vnms/ncs/homes/admin/.ssh/id_dsa");

			Properties properties = new Properties();
			properties.put("StrictHostKeyChecking", "no");
			session.setConfig(properties);

			session.connect(10);
			session.setPortForwardingR(remotePort, "localhost",
					localPortToFoward);

			SSHTunnel tunnel = new SSHTunnel();
			tunnel.setJsch(jsch);
			tunnel.setSession(session);

			return tunnel;

		} catch (JSchException e) {
			// TODO Auto-generated catch block
			System.out.println("Error establishing tunnel  for address : "
					+ address + "Exception => " + e);
		}

		return null;
	}

	private void removeUnManagedAddresses(
			Map<String, HostTarget> rechabilityAddressesMap) {

		List<String> unManagedDeviceAddresses = new ArrayList<>();
		for (Map.Entry<String, SSHTunnel> entry : sshTunnelMap.entrySet()) {

			if (rechabilityAddressesMap.containsKey(entry.getKey()) == false) {
				SSHTunnel unManagedTunnel = entry.getValue();
				// Optimize if not reachable
				if (unManagedTunnel.getSession().isConnected()) {
					unManagedTunnel.getSession().disconnect();
				}

				unManagedDeviceAddresses.add(entry.getKey());

			}

		}

		for (String unManagedAddress : unManagedDeviceAddresses) {
			sshTunnelMap.remove(unManagedAddress);
		}

	}

	private List<String> getDeviceAddress() {

		List<String> ipAddresses = new ArrayList<>();
		ipAddresses.add("10.192.14.87");
		ipAddresses.add("10.192.63.161");

		return ipAddresses;
	}

	public static void main(String[] args) {

		JedisSubscriber subscriber = new JedisSubscriber();
		subscriber.subscribeAndListen();
		
		SSHTunnelManager manager = new SSHTunnelManager();
		try {
			manager.checkAndUpdate();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
