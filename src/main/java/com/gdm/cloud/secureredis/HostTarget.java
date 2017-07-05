package com.gdm.cloud.secureredis;

public class HostTarget {
	
	String ipAddress;
	Boolean isReachable;
	String errrorMessage;
	public String getIpAddress() {
		return ipAddress;
	}
	public Boolean getIsReachable() {
		return isReachable;
	}
	public String getErrrorMessage() {
		return errrorMessage;
	}
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	public void setIsReachable(Boolean isReachable) {
		this.isReachable = isReachable;
	}
	public void setErrrorMessage(String errrorMessage) {
		this.errrorMessage = errrorMessage;
	}

}
