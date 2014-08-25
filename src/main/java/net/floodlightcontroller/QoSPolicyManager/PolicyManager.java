package net.floodlightcontroller.QoSPolicyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.qos.IQoSService;
import net.floodlightcontroller.qos.QoSPolicy;
import net.floodlightcontroller.qos.QoSWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IStorageSourceService;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.rmi.runtime.Log;

public class PolicyManager implements IOFMessageListener, IFloodlightModule,
		IPolicyManager {

	protected IStorageSourceService storageSource;
	protected IQoSService QoSService;
	protected IRestApiService restApi;
	protected List<PolicyRestriction> restrictions; // Synchronized
	public static final String TABLE_NAME = "controller_policy_restriction";
	public static final String COLUMN_RID = "restriction-id";
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_MATCH_PROTOCOL = "protocol";
	public static final String COLUMN_MATCH_ETHTYPE = "eth-type";
	public static final String COLUMN_MATCH_TCPUDP_SRCPRT = "tcpudpsrcport";
	public static final String COLUMN_MATCH_TCPUDP_DSTPRT = "tcpudpdstport";
	public static final String COLUMN_NW_TOS = "nw_tos";
	public static final String COLUMN_MATCH_PRIORITY = "priority";
	public static final String COLUMN_MATCH_QUEUE = "queue";
	public static String ColumnNames[] = { COLUMN_RID, COLUMN_NAME,
			COLUMN_MATCH_PROTOCOL, COLUMN_MATCH_ETHTYPE,
			COLUMN_MATCH_TCPUDP_SRCPRT, COLUMN_MATCH_TCPUDP_DSTPRT,
			COLUMN_NW_TOS, COLUMN_MATCH_PRIORITY, COLUMN_MATCH_QUEUE };

	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "policy_manager";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return (type.equals(OFType.PACKET_IN) && (name.equals("topology") || name
				.equals("devicemanager")));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return (type.equals(OFType.PACKET_IN) && name.equals("QoSManager"));
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IPolicyManager.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IPolicyManager.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IQoSService.class);
		l.add(IStorageSourceService.class);
		l.add(IPolicyManager.class);
		return l;
	}

	public List<PolicyRestriction> getRestrictions() {
		return this.restrictions;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		this.QoSService = context.getServiceImpl(IQoSService.class);
		this.storageSource = context
				.getServiceImpl(IStorageSourceService.class);
		this.restApi = context.getServiceImpl(IRestApiService.class);
		logger = LoggerFactory.getLogger(IPolicyManager.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new RestrictionWebRoutable());
		storageSource.createTable(TABLE_NAME, null);
		storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_RID);
		restrictions = new ArrayList<PolicyRestriction>();
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		OFPacketIn pi = (OFPacketIn) msg;
		if (!QoSService.isEnabled()) {
			return Command.CONTINUE;
		}
		// logger.debug("Message Recieved: Type - {}",msg.getType().toString());
		// Listen for Packets that match Policies
		switch (msg.getType()) {
		case PACKET_IN:
			OFMatch match = new OFMatch();
			match.loadFromPacket(pi.getPacketData(), pi.getInPort());
			generatePolicy(match);
			break;
		default:
			return Command.CONTINUE;
		}
		return Command.CONTINUE;
	}

	public void generatePolicy(OFMatch match) {
		if (match.getNetworkProtocol() == 1 || match.getNetworkProtocol() == 2
				|| match.getNetworkDestination() == 0
				|| match.getNetworkSource() == 0)
			return;
		logger.info("checking the match restrictions...");
		PolicyRestriction r = getMatchRestriction(match);
		if (r == null){
			logger.info("no match restrictions");
			return;
		}
		logger.info("Generating Policy");
		QoSPolicy policy = restrictionToPolicy(match, r); // if generated policy
															// exist, it should
															// be disregarded in
															// the method
															// "addpolicy"
		QoSService.addPolicy(policy);
	}

	public synchronized void addRestriction(PolicyRestriction restriction) {
		logger.debug("Adding restriction to List and Storage");
		restriction.restrictionid = restriction.genID();
		int p = 0;
		for (p = 0; p < this.restrictions.size(); p++) {
			// check if empy
			if (this.restrictions.isEmpty()) {
				// p is zero
				break;
			}
			// starts at the first(lowest) policy based on priority
			// insertion sort, gets hairy when n # of switches increases.
			// larger networks may need a merge sort.
			if (this.restrictions.get(p).priority >= restriction.priority) {
				// this keeps "p" in the correct position to place new policy in
				break;
			}
		}
		if (p <= this.restrictions.size()) {
			this.restrictions.add(p, restriction);
		} else {
			this.restrictions.add(restriction);
		}
		// Add to the storageSource
		Map<String, Object> restrictionEntry = new HashMap<String, Object>();
		restrictionEntry.put(COLUMN_RID,
				Long.toString(restriction.restrictionid));
		restrictionEntry.put(COLUMN_NAME, restriction.name);
		restrictionEntry.put(COLUMN_MATCH_PROTOCOL,
				Short.toString(restriction.protocol));
		restrictionEntry.put(COLUMN_MATCH_ETHTYPE,
				Short.toString(restriction.ethtype));
		restrictionEntry.put(COLUMN_MATCH_TCPUDP_SRCPRT,
				Short.toString(restriction.tcpudpsrcport));
		restrictionEntry.put(COLUMN_MATCH_TCPUDP_DSTPRT,
				Short.toString(restriction.tcpudpdstport));
		restrictionEntry.put(COLUMN_MATCH_TCPUDP_DSTPRT,
				Short.toString(restriction.tcpudpdstport));
		storageSource.insertRow(TABLE_NAME, restrictionEntry);

	}
	
	public synchronized void deleteRestriction(PolicyRestriction restriction) {
		Map<String, Object> restrictionEntry = new HashMap<String, Object>();
		restrictionEntry.put(COLUMN_RID,
				Long.toString(restriction.restrictionid));
		restrictionEntry.put(COLUMN_NAME, restriction.name);
		restrictionEntry.put(COLUMN_MATCH_PROTOCOL,
				Short.toString(restriction.protocol));
		restrictionEntry.put(COLUMN_MATCH_ETHTYPE,
				Short.toString(restriction.ethtype));
		restrictionEntry.put(COLUMN_MATCH_TCPUDP_SRCPRT,
				Short.toString(restriction.tcpudpsrcport));
		restrictionEntry.put(COLUMN_MATCH_TCPUDP_DSTPRT,
				Short.toString(restriction.tcpudpdstport));
		restrictionEntry.put(COLUMN_MATCH_TCPUDP_DSTPRT,
				Short.toString(restriction.tcpudpdstport));
	
		storageSource.deleteRow(TABLE_NAME, restrictionEntry);
		Iterator<PolicyRestriction> sIter = this.restrictions.iterator();
		while(sIter.hasNext()){
			PolicyRestriction pm = sIter.next();
			if(pm.restrictionid == restriction.restrictionid){
				sIter.remove();
				break; //done only one can exist
			}
		}
	
	}

	private boolean checkIfRestrictionExists(PolicyRestriction Restriction,
			List<PolicyRestriction> Restrictions) {
		Iterator<PolicyRestriction> pIter = Restrictions.iterator();
		while (pIter.hasNext()) {
			PolicyRestriction r = pIter.next();
			if (Restriction.isSameAs(r) || Restriction.name.equals(r.name)) {
				return true;
			}
		}
		return false;
	}

	public PolicyRestriction getMatchRestriction(OFMatch match) {
		List<PolicyRestriction> matchRestrictions = new ArrayList<PolicyRestriction>();
		Iterator<PolicyRestriction> pIter = restrictions.iterator();
		while (pIter.hasNext()) {
			PolicyRestriction r = pIter.next();
			if (r.ethtype != -1) {
				if (r.ethtype != match.getDataLayerType())
					continue;
			}
			if (r.protocol != -1) {
				if (r.protocol != match.getNetworkProtocol())
					continue;
			}
			if (r.tos != -1) {
				if (r.tos != match.getNetworkTypeOfService())
					continue;
			}
			if (r.tcpudpsrcport != -1) {
				if (r.tcpudpsrcport != match.getTransportSource())
					;
			}
			if (r.tcpudpdstport != -1) {
				if (r.tcpudpdstport != match.getTransportDestination())
					continue;
			}

			matchRestrictions.add(r);
		}
		if (matchRestrictions.isEmpty()) {
			return null;
		}
		pIter = matchRestrictions.iterator();
		PolicyRestriction target = pIter.next();
		PolicyRestriction r;
		while (pIter.hasNext()) {
			r = pIter.next();
			if (r.priority > target.priority)
				target = r;
		}
		return target;
	}

	public QoSPolicy restrictionToPolicy(OFMatch match, PolicyRestriction r) {
		// TODO Auto-generated method stub
		QoSPolicy policy = new QoSPolicy();
		policy.ipsrc = match.getNetworkSource();
		String name = r.name;
		if (r.ethtype != -1) {
			policy.ethtype = r.ethtype;
			name = name + " ethtype(" + r.ethtype + ")";
		} else {
			policy.ethtype = match.getDataLayerType(); // default

		}
		if (r.tcpudpdstport != -1){
			policy.tcpudpdstport = r.tcpudpdstport;
			name = name + " TcpDstPort(" + r.tcpudpdstport + ")";
		}
		if (r.tcpudpsrcport != -1){
			policy.tcpudpsrcport = r.tcpudpsrcport;
			name = name + " TcpSrcPort(" + r.tcpudpsrcport + ")";
		}
		if (r.protocol != -1){
			policy.protocol = r.protocol;
			name = name + " Protocol(" + r.protocol + ")";
		}
		else
			policy.protocol = match.getNetworkProtocol(); // default
		if (r.priority != -1){
			policy.priority = r.priority;
			name = name + " Priority(" + r.protocol + ")";
		}
		policy.queue = r.queue;
		policy.name = name;
		policy.source = r.name;  // if user directly add policy by GUI this field should be null in that policy
		return policy;
	}

}
