package net.floodlightcontroller.QoSPolicyManager;

import java.util.List;

import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.qos.QoSPolicy;

public interface IPolicyManager extends IFloodlightService {
	
	public void generatePolicy(OFMatch match);
	
	public void addRestriction(PolicyRestriction restriction);
	
	public PolicyRestriction getMatchRestriction(OFMatch match);
	
	public QoSPolicy restrictionToPolicy(OFMatch match, PolicyRestriction r);
	
	public List<PolicyRestriction> getPolicies();
	
	
}
