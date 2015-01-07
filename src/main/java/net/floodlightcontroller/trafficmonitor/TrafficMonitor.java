package net.floodlightcontroller.trafficmonitor;

import java.util.ArrayList;
import java.util.Collection;

import java.util.Map;

import org.openflow.protocol.OFFeaturesReply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.qos.QoSWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;


public class TrafficMonitor implements IFloodlightModule {

	protected static Logger log ;
	boolean bandwidth_monitor = false;
	protected IFloodlightProviderService floodlightProvider;
	BandWidthMonitor BWM ;
	BandWidthMonitorThread BWMT;
	
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
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		       log =  LoggerFactory.getLogger(TrafficMonitor.class);
		       floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);

		       BWMT = new BandWidthMonitorThread();
		
	}
	

	@Override
	public void startUp(FloodlightModuleContext context) {
		// TODO Auto-generated method stub
        
		BWM = new BandWidthMonitor(floodlightProvider);
		if(bandwidth_monitor)
		BWMT.start();
		
	}
	
	protected class BandWidthMonitorThread extends Thread{
		
		public void run() {
            while(true){
            	BWM.retrieve();
            	try {
                    Thread.sleep(3000);  // 3secs a loop
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for statistics", e);
                }
            	/*  TODO  we have get OFRequest from switch every 3 sec and but have not done 
            	 * about monitor the bandwidth , see the website provided in the file to know
            	 * detail about how to implement the function
            	 */
            	
            	
            }
        }
		
		
	}
	
	
	
	
	
	

}
