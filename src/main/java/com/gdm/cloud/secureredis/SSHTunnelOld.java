package com.gdm.cloud.secureredis;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHTunnelOld {
	final private int remoteSSHPort = 22;
	final private int remotePort = 8888;
	
	final private int localPortToFoward = 6379; // for eg:redis
	 
	
	
	private String remoteIpAddress;
	
	
	SSHTunnelOld(String remoteIpAddress) {
		this.remoteIpAddress = remoteIpAddress;
	}
	
	private void establishTunnel() {
		
		JSch jsch=new JSch();
		
		try {
			Session session=jsch.getSession("admin", remoteIpAddress, remoteSSHPort);
			jsch.addIdentity("/var/versa/vnms/ncs/homes/admin/.ssh/id_dsa");
			
			Properties properties = new Properties();
			properties.put("StrictHostKeyChecking", "no");
			session.setConfig(properties);
			
			session.connect(10);
			
			session.setPortForwardingR(remotePort, "localhost", localPortToFoward);
			
		
			/*
			session.isConnected();
			session.disconnect();
			session.connect(); */
			
			
		} catch (JSchException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		System.out.println("");
		
	    List<String> ipAddresses = new ArrayList<>();
	    ipAddresses.add("10.192.14.87");
	    ipAddresses.add("10.192.63.161");
	    
	    for (String address : ipAddresses) {
	
	    	SSHTunnelOld sshTunnel = new SSHTunnelOld(address);
			sshTunnel.establishTunnel();
		
		}
		
		try {
			Thread.sleep(Integer.MAX_VALUE);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}
