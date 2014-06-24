/**
 *    Copyright 2011, Big Switch Networks, Inc. 
 *    Originally created by David Erickson, Stanford University
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.QoSManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.qos.*;
import net.floodlightcontroller.routing.ForwardingBase;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jimmyoic / kao / james1201
 *
 */
/**
 
 *
 */
public class QoSManager implements IFloodlightModule, IOFMessageListener,
		IDeviceListener {
	protected static Logger log = LoggerFactory.getLogger(QoSManager.class);
	protected IDeviceService deviceManager;
	protected IFloodlightProviderService floodlightProvider;
	protected IQoSService QoSService;
	protected ITopologyService topology;
	protected IRoutingService routingEngine;
	protected ICounterStoreService counterStore;
	protected String qosJson = new String();
	protected OFMessageDamper messageDamper;
	protected IRestApiService restApi;
	protected QoSPolicy policy;
	public static final int FORWARDING_APP_ID = 2;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "QoSManager";
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
		return (type.equals(OFType.PACKET_IN) && (name.equals("fowarding")));
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub

		if (QoSService.isEnabled()) {
			switch (msg.getType()) {
			case PACKET_IN:
				checkPolicy(sw, (OFPacketIn) msg, cntx, false);
			default:
				break;
			}
		}

		return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IQoSService.class);
		l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		l.add(IRoutingService.class);
		l.add(IRestApiService.class);
		l.add(ITopologyService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		this.floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		this.restApi = context.getServiceImpl(IRestApiService.class);
		this.topology = context.getServiceImpl(ITopologyService.class);
		this.routingEngine = context.getServiceImpl(IRoutingService.class);
		this.counterStore = context.getServiceImpl(ICounterStoreService.class);
		this.deviceManager = context.getServiceImpl(IDeviceService.class);
		this.QoSService = context.getServiceImpl(IQoSService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// TODO Auto-generated method stub
		deviceManager.addListener(this);
		restApi.addRestletRoutable(new QoSManagerWebRoutable());
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

	}

	public Comparator<SwitchPort> clusterIdComparator = new Comparator<SwitchPort>() {
		@Override
		public int compare(SwitchPort d1, SwitchPort d2) {
			Long d1ClusterId = topology.getL2DomainId(d1.getSwitchDPID());
			Long d2ClusterId = topology.getL2DomainId(d2.getSwitchDPID());
			return d1ClusterId.compareTo(d2ClusterId);
		}
	};

	
	/**
	 * take part of the method in Forwarding module that we can use that to find
	 * the path  between src and dst.
	 * 
	 * this method first check is there is a
	 * match in policies, if false, return and forwarding the packet
	 * 
	 * 
	 * if true, then we find the switch that is on the path
	 * and add flow into each of them
	 * 
	 * 
	 * may be simplified
	 * @param sw
	 * @param pi
	 * @param cntx
	 * @param requestFlowRemovedNotifn
	 * 
	 
	 */
	protected void checkPolicy(IOFSwitch sw, OFPacketIn pi,
			FloodlightContext cntx, boolean requestFlowRemovedNotifn) {
		OFMatch match = new OFMatch();

		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		
		policy = checkIfPolicyMatch(match); // copy if there is a policy
											// related to the source and
											// destination
		if (policy == null) // if that is true , count the path (sw) , adding
							// switch info and addflow to switch
			return;
		// Check if we have the location of the destination
		IDevice dstDevice = IDeviceService.fcStore.get(cntx,
				IDeviceService.CONTEXT_DST_DEVICE);

		if (dstDevice != null) {
			IDevice srcDevice = IDeviceService.fcStore.get(cntx,
					IDeviceService.CONTEXT_SRC_DEVICE);
			Long srcIsland = topology.getL2DomainId(sw.getId());

			if (srcDevice == null) {
				log.debug("No device entry found for source device");
				return;
			}

			if (srcIsland == null) {
				log.debug("No openflow island found for source {}/{}",
						sw.getStringId(), pi.getInPort());
				return;
			}

			// Validate that we have a destination known on the same island
			// Validate that the source and destination are not on the same
			// switchport
			boolean on_same_island = false;
			boolean on_same_if = false;
			for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
				long dstSwDpid = dstDap.getSwitchDPID();
				Long dstIsland = topology.getL2DomainId(dstSwDpid);
				if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
					on_same_island = true;
					if ((sw.getId() == dstSwDpid)
							&& (pi.getInPort() == dstDap.getPort())) {
						on_same_if = true;
					}
					break;
				}
			}

			if (!on_same_island) {
				// Flood since we don't know the dst device
				if (log.isTraceEnabled()) {
					log.trace("No first hop island found for destination "
							+ "device {}, Action = flooding", dstDevice);
				}

				return;
			}

			if (on_same_if) {
				if (log.isTraceEnabled()) {
					log.trace("Both source and destination are on the same "
							+ "switch/port {}/{}, Action = NOP", sw.toString(),
							pi.getInPort());
				}
				return;
			}

			// Install all the routes where both src and dst have attachment
			// points. Since the lists are stored in sorted order we can
			// traverse the attachment points in O(m+n) time
			SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
			Arrays.sort(srcDaps, clusterIdComparator);
			SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();
			Arrays.sort(dstDaps, clusterIdComparator);

			int iSrcDaps = 0, iDstDaps = 0;

			while ((iSrcDaps < srcDaps.length) && (iDstDaps < dstDaps.length)) {
				SwitchPort srcDap = srcDaps[iSrcDaps];
				SwitchPort dstDap = dstDaps[iDstDaps];
				Long srcCluster = topology
						.getL2DomainId(srcDap.getSwitchDPID());
				Long dstCluster = topology
						.getL2DomainId(dstDap.getSwitchDPID());

				int srcVsDest = srcCluster.compareTo(dstCluster);
				if (srcVsDest == 0) {
					if (!srcDap.equals(dstDap) && (srcCluster != null)
							&& (dstCluster != null)) {
						Route route = routingEngine.getRoute(
								srcDap.getSwitchDPID(),
								(short) srcDap.getPort(),
								dstDap.getSwitchDPID(),
								(short) dstDap.getPort());
						if (route != null) {
							if (log.isTraceEnabled()) {
								log.trace("pushRoute match={} route={} "
										+ "destination={}:{}", new Object[] {
										match, route, dstDap.getSwitchDPID(),
										dstDap.getPort() });
							}

							List<NodePortTuple> switchPortList = route
									.getPath();

							for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
								// indx and indx-1 will always have the same
								// switch DPID.
								long switchDPID = switchPortList.get(indx)
										.getNodeId();
								IOFSwitch sww = floodlightProvider
										.getSwitches().get(switchDPID);
								short outPort = switchPortList.get(indx)
										.getPortId();

								setEnqueuePort(policy, outPort, sww);

								// add the enqueue to the switch on the path ,
								// source and destination
								System.out.println("ADDing Policy: "
										+ policy.name + policy.ipsrc + " "
										+ policy.ipdst + " " + policy.sw
										+ " enqueport: " + policy.enqueueport
										+ "\n\n");
								QoSService.addPolicyToSwitch(policy);

							}
						}
					}
					iDstDaps++;
				} else if (srcVsDest < 0) {
					iSrcDaps++;
				} else {
					iDstDaps++;
				}
			}
		} else {
			// Flood since we don't know the dst device
			return;
		}
	}
    
	/**
	 * set the enque port to the switch we found on the path
	 * @param p
	 * @param outputPort
	 * @param sw
	 */
	public void setEnqueuePort(QoSPolicy p, short outputPort, IOFSwitch sw) {
		p.sw = sw.getStringId();
		p.enqueueport = outputPort;
		return;
	}

	public QoSPolicy checkIfPolicyIsThere(int src, int dst) { // test if there
																// is actually
																// src and dst
																// (necessary)
		List<QoSPolicy> policies = QoSService.getPolicies();
		Iterator<QoSPolicy> pIter = policies.iterator();
		while (pIter.hasNext()) {
			QoSPolicy p = pIter.next();
			if ((p.ipsrc == src && p.ipdst == dst)) {
				System.out.println(p.name + "\n\n" + p.ipsrc + "  " + p.ipdst);
				return p;
			}

		}
		return null;

	}
     
	/**
	 * copy the policy avoiding change the content in the storage & policies
	 * @param policy
	 * @return
	 */
	QoSPolicy copyQoSPolicy(QoSPolicy policy) {
		QoSPolicy p = new QoSPolicy();
		p.ethdst = policy.ethdst;
		p.ethsrc = policy.ethsrc;
		p.name = policy.name;
		p.ethtype = policy.ethtype;
		p.ingressport = policy.ingressport;
		p.ipdst = policy.ipdst;
		p.ipsrc = policy.ipsrc;
		p.policyid = policy.policyid;
		p.priority = policy.priority;
		p.protocol = policy.protocol;
		p.queue = policy.queue;
		p.service = policy.service;
		p.sw = policy.sw;
		p.tcpudpdstport = policy.tcpudpdstport;
		p.tcpudpsrcport = policy.tcpudpsrcport;
		p.tos = policy.tos;
		p.vlanid = policy.vlanid;
		return p;
	}

	/**
	 *  when Packet_in, first find if there is a policy match the content of the packetin (may be IPsrt or IPdst, even both of them)
	 *  return the one which has the highest priority among those match policies
	 * 
	 *  if two policies have the same priority, return the one earlier inputed to the policies
	 * 
	 * @param match
	 * @return
	 */
	public QoSPolicy checkIfPolicyMatch(OFMatch match) {
   
		List<QoSPolicy> policies = QoSService.getPolicies();
		List<QoSPolicy> matchPolicies = new ArrayList<QoSPolicy>();

		/* Check match Policies and add them into "matchPolices" */
		Iterator<QoSPolicy> pIter = policies.iterator();
		
		/* we should know that there may be some policy only restrict some particular Ipsrc or Ipdst
		 * so if there is a policy which ipsrc(dst) has a -1 value, it means in that policy we don't
		 * care that value. For example if ipsrc= -1, ipdst= h2, it means that policy deals with all
		 * the packet sent to h2.
		 * 
		 * note that they should not both equal to -1 in the same policy
		 */
		while (pIter.hasNext()) {
			QoSPolicy p = pIter.next();
			QoSPolicy r = new QoSPolicy();
			r = copyQoSPolicy(p);

			if (p.ipsrc == -1)            // there will be some policies just limit some particular ip destination, some p.ipsrc may be -1
				r.ipsrc = match.getNetworkSource(); 
			else if (p.ipsrc != match.getNetworkSource()) // if not match, check the next policy in policies
				continue;
			else
				r.ipsrc = p.ipsrc;

			if (p.ipdst == -1)  // same reason to the previous one
				r.ipdst = match.getNetworkDestination();
			else if (p.ipdst != match.getNetworkDestination())
				continue;
			else
				r.ipdst = p.ipdst;

			if (p.protocol == -1)               
				r.protocol = match.getNetworkProtocol();
			else if (p.protocol != match.getNetworkProtocol())
				continue;
			else
				r.protocol = p.protocol;

			matchPolicies.add(r);  // collect all the match policies into a list, later choose the policy with the highest priority
		}

		if (matchPolicies.isEmpty())
			return null;

		/* Compare Priority in the matchPolicies */
		pIter = matchPolicies.iterator();
		QoSPolicy q = pIter.next();

		while (pIter.hasNext()) {

			QoSPolicy p = pIter.next();

			if (p.priority > q.priority)
				q = copyQoSPolicy(p);
		}

		return q;
	}

	protected OFMatch wildcard(OFMatch match, IOFSwitch sw,
			Integer wildcard_hints) {
		if (wildcard_hints != null) {
			return match.clone().setWildcards(wildcard_hints.intValue());
		}
		return match.clone();
	}

	@Override
	public void deviceAdded(IDevice device) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deviceRemoved(IDevice device) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deviceMoved(IDevice device) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deviceIPV4AddrChanged(IDevice device) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deviceVlanChanged(IDevice device) {
		// TODO Auto-generated method stub

	}

}
