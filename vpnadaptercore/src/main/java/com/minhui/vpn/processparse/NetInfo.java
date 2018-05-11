package com.minhui.vpn.processparse;

public class NetInfo {

    private int uid;
    private int port;
    private String address;
    private int type;
    private long ip;
    private int sourPort;

    public int getSourPort() {
        return sourPort;
    }

    public void setSourPort(int sourPort) {
        this.sourPort = sourPort;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getIp() {
        return ip;
    }

    public void setIp(long ip) {
        this.ip = ip;
    }
}
