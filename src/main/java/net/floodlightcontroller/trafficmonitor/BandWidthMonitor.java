package net.floodlightcontroller.trafficmonitor;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.types.MacVlanPair;

import net.floodlightcontroller.core.web.SwitchResourceBase;
import net.floodlightcontroller.core.web.SwitchResourceBase.REQUESTTYPE;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BandWidthMonitor {
	public enum REQUESTTYPE {
		OFSTATS, OFFEATURES
	}

	IFloodlightProviderService floodlightProvider;
	protected static Logger log = LoggerFactory
			.getLogger(BandWidthMonitor.class);

	BandWidthMonitor(IFloodlightProviderService floodlightProvider){
		this.floodlightProvider = floodlightProvider;
	}
	
	
	
	
	public Map<String, Object> retrieve() {
		String statType = "port";
		return retrieveInternal(statType);
	}

	public Map<String, Object> retrieveInternal(String statType) {
		HashMap<String, Object> model = new HashMap<String, Object>();

		OFStatisticsType type = OFStatisticsType.PORT;
		REQUESTTYPE rType = REQUESTTYPE.OFSTATS;;

		Long[] switchDpids = floodlightProvider.getSwitches().keySet()
				.toArray(new Long[0]);
		List<GetConcurrentStatsThread> activeThreads = new ArrayList<GetConcurrentStatsThread>(
				switchDpids.length);
		List<GetConcurrentStatsThread> pendingRemovalThreads = new ArrayList<GetConcurrentStatsThread>();
		GetConcurrentStatsThread t;

		for (Long l : switchDpids) {
			t = new GetConcurrentStatsThread(l, rType, type);
			activeThreads.add(t);
			t.start();
		}
		
		// Join all the threads after the timeout. Set a hard timeout
		// of 12 seconds for the threads to finish. If the thread has not
		// finished the switch has not replied yet and therefore we won't
		// add the switch's stats to the reply.
		for (int iSleepCycles = 0; iSleepCycles < 12; iSleepCycles++) {
			for (GetConcurrentStatsThread curThread : activeThreads) {
				if (curThread.getState() == State.TERMINATED) {
					if (rType == REQUESTTYPE.OFSTATS) {
						model.put(
								HexString.toHexString(curThread.getSwitchId()),
								curThread.getStatisticsReply());
					} else if (rType == REQUESTTYPE.OFFEATURES) {
						model.put(
								HexString.toHexString(curThread.getSwitchId()),
								curThread.getFeaturesReply());
					}
					pendingRemovalThreads.add(curThread);
				}
			}

			// remove the threads that have completed the queries to the
			// switches
			for (GetConcurrentStatsThread curThread : pendingRemovalThreads) {
				activeThreads.remove(curThread);
			}
			// clear the list so we don't try to double remove them
			pendingRemovalThreads.clear();

			// if we are done finish early so we don't always get the worst case
			if (activeThreads.isEmpty()) {
				break;
			}

			// sleep for 1 s here
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.error("Interrupted while waiting for statistics", e);
			}
		}
	
		for (Object key : model.keySet()) {
			CopyOnWriteArrayList<?> CA = (CopyOnWriteArrayList<?>) model.get(key);
			Iterator<?> it = CA.iterator();
			while (it.hasNext()) {
				System.out.println(it.next() + "asdasdasdasd\n\n");

			}

		}

		return model;
	}

	protected class GetConcurrentStatsThread extends Thread {
		private List<OFStatistics> switchReply;
		private long switchId;
		private OFStatisticsType statType;
		private REQUESTTYPE requestType;
		private OFFeaturesReply featuresReply;
		private Map<MacVlanPair, Short> switchTable;

		public GetConcurrentStatsThread(long switchId, REQUESTTYPE requestType,
				OFStatisticsType statType) {
			this.switchId = switchId;
			this.requestType = requestType;
			this.statType = statType;
			this.switchReply = null;
			this.featuresReply = null;
			this.switchTable = null;
		}

		public List<OFStatistics> getStatisticsReply() {
			return switchReply;
		}

		public OFFeaturesReply getFeaturesReply() {
			return featuresReply;
		}

		public Map<MacVlanPair, Short> getSwitchTable() {
			return switchTable;
		}

		public long getSwitchId() {
			return switchId;
		}

		public void run() {
			if ((requestType == REQUESTTYPE.OFSTATS) && (statType != null)) {		
				switchReply = getSwitchStatistics(switchId, statType);
			}
		}

		protected List<OFStatistics> getSwitchStatistics(long switchId,
				OFStatisticsType statType) {

			IOFSwitch sw = floodlightProvider.getSwitches().get(switchId);
			Future<List<OFStatistics>> future;
			List<OFStatistics> values = null;
			if (sw != null) {
				OFStatisticsRequest req = new OFStatisticsRequest();
				req.setStatisticType(statType);
				int requestLength = req.getLengthU();
				if (statType == OFStatisticsType.FLOW) {
					OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
					OFMatch match = new OFMatch();
					match.setWildcards(0xffffffff);
					specificReq.setMatch(match);
					specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
					specificReq.setTableId((byte) 0xff);
					req.setStatistics(Collections
							.singletonList((OFStatistics) specificReq));
					requestLength += specificReq.getLength();
				} else if (statType == OFStatisticsType.AGGREGATE) {
					OFAggregateStatisticsRequest specificReq = new OFAggregateStatisticsRequest();
					OFMatch match = new OFMatch();
					match.setWildcards(0xffffffff);
					specificReq.setMatch(match);
					specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
					specificReq.setTableId((byte) 0xff);
					req.setStatistics(Collections
							.singletonList((OFStatistics) specificReq));
					requestLength += specificReq.getLength();
				} else if (statType == OFStatisticsType.PORT) {
					OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
					specificReq.setPortNumber((short) OFPort.OFPP_NONE
							.getValue());
					req.setStatistics(Collections
							.singletonList((OFStatistics) specificReq));
					requestLength += specificReq.getLength();
				} else if (statType == OFStatisticsType.QUEUE) {
					OFQueueStatisticsRequest specificReq = new OFQueueStatisticsRequest();
					specificReq.setPortNumber((short) OFPort.OFPP_ALL
							.getValue());
					// LOOK! openflowj does not define OFPQ_ALL! pulled this
					// from openflow.h
					// note that I haven't seen this work yet though...
					specificReq.setQueueId(0xffffffff);
					req.setStatistics(Collections
							.singletonList((OFStatistics) specificReq));
					requestLength += specificReq.getLength();
				} else if (statType == OFStatisticsType.DESC
						|| statType == OFStatisticsType.TABLE) {
					// pass - nothing todo besides set the type above
				}
				req.setLengthU(requestLength);
				try {
					future = sw.getStatistics(req);
					values = future.get(10, TimeUnit.SECONDS);
				} catch (Exception e) {
					log.error(
							"Failure retrieving statistics from switch " + sw,
							e);
				}
			}
			return values;
		}
	}

}
