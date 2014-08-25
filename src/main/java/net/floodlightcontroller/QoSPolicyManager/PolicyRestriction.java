package net.floodlightcontroller.QoSPolicyManager;

import net.floodlightcontroller.qos.QoSPolicy;

import org.openflow.util.HexString;

public class PolicyRestriction implements Comparable<PolicyRestriction> {

	public long restrictionid;
	public String name;
	public short ethtype;
	public byte protocol;
	public byte tos;
	public short tcpudpsrcport;
	public short tcpudpdstport;
	public short priority=0;
	public short queue;
		
	public PolicyRestriction(){
		this.restrictionid = 0;
		this.name = null;
		this.ethtype = -1;
		this.protocol = -1;
		this.tos = -1;
		this.tcpudpdstport = -1;
		this.tcpudpsrcport = -1;		
		this.priority = 0;
		this.queue=1;
	}
	
	public int genID() {
        int uid = this.hashCode();
        if (uid < 0) {
            uid = uid * 15551;
            uid = Math.abs(uid);
        }
        return uid;
    }
	
	public int compareTo(PolicyRestriction r) {
		return this.priority - ((PolicyRestriction)r).priority;
    }
	public boolean isSameAs(PolicyRestriction r){
		//check object and unique name of policy
		if (this.equals(r) || this.name.equals(r.name)){
			return true;
		}
		else{
			return false;
		}
	}
	
	
	public int hashCode(){
		final int prime = 2521;
	    int result = super.hashCode();
	    result = prime * result + (int) restrictionid;
	    if(name != null){result = prime * result + name.hashCode();}
	    result = prime * result + (int) ethtype;
	    result = prime * result + (int) protocol;
	    result = prime * result + (int) tos;
	    result = prime * result + (int) tcpudpsrcport;
	    result = prime * result + (int) tcpudpdstport;	
	    result = prime * result + (int) priority;
	    return result;
		}
	
}
