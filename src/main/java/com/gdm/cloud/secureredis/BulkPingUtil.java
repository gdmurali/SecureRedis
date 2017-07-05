package com.gdm.cloud.secureredis;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;



/**
 * Using Ping example https://docs.oracle.com/javase/8/docs/technotes/guides/io/example/Ping.java
 * illustrating nio usage.
 * 
 * This helps in finding which ip Addresses are reachable and which are not reachable in less than 5 seconds.
 * 
 * @author murali
 *
 */
public class BulkPingUtil {

	// The default SSH  port
	static int SSH_PORT = 22;

	// The port we'll actually use
	static int port = SSH_PORT;
	



	// Representation of a ping target
	//
	static class Target {


		InetSocketAddress address;
		SocketChannel channel;
		Exception failure;
		long connectStart;
		long connectFinish = 0;
		boolean shown = false;
		String result;

		Target(String host) {
			try {
				address = new InetSocketAddress(InetAddress.getByName(host),
						port);
			} catch (IOException x) {
				failure = x;
			}
		}

		void show() {
			//String result;
			if (connectFinish != 0)
				result = Long.toString(connectFinish - connectStart) + "ms";
			else if (failure != null)
				result = failure.toString();
			else
				result = "Timed out";
			//System.out.println(address + " : " + result);
			////LOGGER.debug(address + " : " + result);
			shown = true;
		}

	}


	// Thread for printing targets as they're heard from
	//
	static class Printer
	extends Thread
	{
		LinkedList<Target> pending = new LinkedList<>();

		Printer() {
			setName("BulkPingPrinter");
			setDaemon(true);
		}

		void add(Target t) {
			synchronized (pending) {
				pending.add(t);
				pending.notify();
			}
		}

		public void run() {
			try {
				for (;;) {
					Target t = null;
					synchronized (pending) {
						while (pending.size() == 0)
							pending.wait();
						t = (Target)pending.removeFirst();
					}
					t.show();
				}
			} catch (InterruptedException x) {
				//LOGGER.debug("Thread interrupted returning");
				return;
			}
		}

	}


	// Thread for connecting to all targets in parallel via a single selector
	//
	static class Connector
	extends Thread
	{
		Selector sel;
		Printer printer;

		// List of pending targets.  We use this list because if we try to
		// register a channel with the selector while the connector thread is
		// blocked in the selector then we will block.
		//
		LinkedList<Target> pending = new LinkedList<>();

		Connector(Printer pr) throws IOException {
			printer = pr;
			sel = Selector.open();
			setName("BulkPingConnector");
		}

		// Initiate a connection sequence to the given target and add the
		// target to the pending-target list
		//
		void add(Target t) {
			SocketChannel sc = null;
			try {

				// Open the channel, set it to non-blocking, initiate connect
				sc = SocketChannel.open();
				sc.configureBlocking(false);

				boolean connected = sc.connect(t.address);

				// Record the time we started
				t.channel = sc;
				t.connectStart = System.currentTimeMillis();

				if (connected) {
					t.connectFinish = t.connectStart;
					sc.close();
					printer.add(t);
				} else {
					// Add the new channel to the pending list
					synchronized (pending) {
						pending.add(t);
					}

					// Nudge the selector so that it will process the pending list
					sel.wakeup();
				}
			} catch (IOException x) {
				if (sc != null) {
					try {
						sc.close();
					} catch (IOException xx) { }
				}
				t.failure = x;
				printer.add(t);
			}
		}

		// Process any targets in the pending list
		//
		private void processPendingTargets() throws IOException {
			synchronized (pending) {
				while (pending.size() > 0) {
					Target t = (Target)pending.removeFirst();
					try {

						// Register the channel with the selector, indicating
						// interest in connection completion and attaching the
						// target object so that we can get the target back
						// after the key is added to the selector's
						// selected-key set
						t.channel.register(sel, SelectionKey.OP_CONNECT, t);

					} catch (IOException x) {

						// Something went wrong, so close the channel and
						// record the failure
						t.channel.close();
						t.failure = x;
						printer.add(t);

					}

				}
			}
		}

		// Process keys that have become selected
		//
		private void processSelectedKeys() throws IOException {
			for (Iterator i = sel.selectedKeys().iterator(); i.hasNext();) {

				// Retrieve the next key and remove it from the set
				SelectionKey sk = (SelectionKey)i.next();
				i.remove();

				// Retrieve the target and the channel
				Target t = (Target)sk.attachment();
				SocketChannel sc = (SocketChannel)sk.channel();

				// Attempt to complete the connection sequence
				try {
					if (sc.finishConnect()) {
						sk.cancel();
						t.connectFinish = System.currentTimeMillis();
						sc.close();
						printer.add(t);
					}
				} catch (IOException x) {
					sc.close();
					t.failure = x;
					printer.add(t);
				}
			}
		}

		volatile boolean shutdown = false;

		// Invoked by the main thread when it's time to shut down
		//
		private void shutdown() {
			shutdown = true;
			sel.wakeup();
		}

		// Connector loop
		//
		public void run() {
			for (;;) {
				try {
					int n = sel.select();
					if (n > 0)
						processSelectedKeys();
					processPendingTargets();
					if (shutdown) {
						sel.close();
						return;
					}
				} catch (IOException x) {
					x.printStackTrace();
				}
			}
		}


	}

	
	/**
	 * 
	 * @param hosts list of ipAddresses
	 * @return list which captures address which is reachable and unreachable along with reason
	 * @throws Exception
	 */
	public static List<HostTarget> getRechabilityStatus(String...hosts) throws Exception {

		List<HostTarget> hostsStatus = new ArrayList<>();

		// Create the threads and start them up
		Printer printer = new Printer();
		printer.start();
		Connector connector = new Connector(printer);
		connector.start();

		// Create the targets and add them to the connector
		LinkedList<Target> targets = new LinkedList<>();
		for (int i = 0; i < hosts.length; i++) {
			Target t = new Target(hosts[i]);
			targets.add(t);
			connector.add(t);
		}

		// Wait for everything to finish
		Thread.sleep(2000);
		connector.shutdown();
		connector.join();
		printer.interrupt();
		printer.join();

		// Print status of targets that have not yet been shown
		for (Iterator i = targets.iterator(); i.hasNext();) {
			Target t = (Target)i.next();
			if (!t.shown)
				t.show();
		}
		
		/** 
		 * Bug 22087: It seems seems some channels/sockets are not closed after this loop.
		 * Closing it explicitly. 
		 */
		//LOGGER.debug("Running loop to make sure socket is closed explicitly");
		for (Iterator i = targets.iterator(); i.hasNext();) {
			Target t = (Target)i.next();
			
			if ( t.channel.isConnected() || t.channel.isOpen() || ! t.channel.socket().isClosed() || t.channel.socket().isConnected() ) {
				////LOGGER.error("Channel or scoket is not closed yet");
			}
			
			try {
				t.channel.close();
				//t.channel.socket().close();
			} catch (Throwable e) {
				//LOGGER.error("Error Closing channel explicitly :" , e);
			}
			
			try {
				//t.channel.close();
				t.channel.socket().close();
			} catch ( Throwable e) {
				//LOGGER.error("Error Closing channel socket explicitly :" , e);
			}
		}


		// Print status of targets that have not yet been shown
		for (Iterator i = targets.iterator(); i.hasNext();) {
			HostTarget hostTarget = new HostTarget();
			Target t = (Target)i.next();
			hostTarget.ipAddress = t.address.getHostString();
			hostTarget.errrorMessage = t.result;
			if ( t.connectFinish > 0 ) {
				hostTarget.isReachable = true;
			} else {
				hostTarget.isReachable = false;
			}
			hostsStatus.add(hostTarget);

		}

		return hostsStatus;

	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		try {
			String[] ips = {"10.192.36.161", "10.192.36.163", "10.192.100.104"};
			long start = System.currentTimeMillis();
			List<HostTarget> result = BulkPingUtil.getRechabilityStatus(ips);
			long end = System.currentTimeMillis();
			System.out.println(end-start); 
			for (HostTarget hostTarget : result) {
				System.out.println("Address = " + hostTarget.ipAddress + " IsReachable = " + hostTarget.isReachable + " Result = " + hostTarget.errrorMessage);
			}
			Thread.sleep(Integer.MAX_VALUE);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/**
		 * Sample usage .. it takes less than 4 seconds to get status of 1000+ nodes :-)
		 */
		/**
		try {
			List<String> addrs  =new ArrayList<>();
			for ( int i =14; i <=17; i++) {
			for(int j=1 ; j<225; j++) {
				String address = "10.192." + i+ "." + j;
				addrs.add(address);
			}
			}
			String[] stringArray = addrs.toArray(new String[0]);
			long start = System.currentTimeMillis();
			List<HostTarget> result = BulkPingUtil.getRechabilityStatus(stringArray);
			long end = System.currentTimeMillis();
			System.out.println(end-start); 
			for (HostTarget hostTarget : result) {
				System.out.println("Address = " + hostTarget.ipAddress + " IsReachable = " + hostTarget.isReachable + " Result = " + hostTarget.errrorMessage);
				
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} */

	}

}
