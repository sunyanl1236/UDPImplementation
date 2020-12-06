package com.udpimplementation.comp445a3;

public class PacketInformation {
	private Packet p;
	private String status;
	
	public PacketInformation() {
		this.p = null;
		this.status = "";
	}
	
	
	public PacketInformation(Packet p, String status) {
		this.p = p;
		this.status = status;
	}


	public Packet getPacket() {
		return p;
	}


	public void setPacket(Packet p) {
		this.p = p;
	}


	public String getStatus() {
//		System.out.println("************get status");
		return status;
	}


	public void setStatus(String status) {
		this.status = status;
//		System.out.println("************set status");
	}
}
