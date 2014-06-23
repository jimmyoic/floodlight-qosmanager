package net.floodlightcontroller.QoSManager;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.qos.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import net.floodlightcontroller.packet.IPv4;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.openflow.util.U16;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/*
* copy QoSPoliciesResource.java
*
*
*/


public class QoSManagerResource extends ServerResource {
	protected static Logger logger = LoggerFactory.getLogger(QoSManagerResource.class);
	String status = null;

	@Get("json")
	public Object handleRequest(){
		
		IQoSService qos = 
                (IQoSService)getContext().getAttributes().
                get(IQoSService.class.getCanonicalName());
		if(qos.isEnabled()){
			// gets the list of policies currently being implemented
	        return qos.getPolicies();
		}
		else{
			status = "Please enable Quality of Service";
			return ("{\"status\" : \"" + status + "\"}");
		}
	}

	 /**
     * Takes a QoS Policy Rule string in JSON format and parses it into
     * our firewall rule data structure, then adds it to the qos polcies storage.
     * @param fmJson The qos policy entry in JSON format.
     * @return A string status message
     */
    @Post
    public String add(String qosJson) {
        System.out.println("[*] QosManager add policy...");
    	IQoSService qos = 
    			(IQoSService)getContext().getAttributes().
    			get(IQoSService.class.getCanonicalName());
    	
    	//dummy policy
    	QoSPolicy policy;
    	try{
    		policy = jsonToPolicy(qosJson);
    	}
    	catch(IOException e){
    		logger.error("Error Parsing Quality of Service Policy to JSON: {}, Error: {}", qosJson, e);
    		e.printStackTrace();
    		return "{\"status\" : \"Error! Could not parse policy, see log for details.\"}";
    	}
    	String status = null;
    	if(checkIfPolicyExists(policy,qos.getPolicies())){
    		status = "Error!, This policy already exists!";
    		logger.error(status);
    	}
    	else{
    		//Only add if enabled ?needed?
    		    if(qos.isEnabled()){
				/**
				 * NOTE: the check for how its added happens inside
				 * addPolicy:(AROUND QoS.java:467)
				 **/
    			if(policy.name == null){
    				return status = "[-] Bad Policy, No Name";}
                /*
                delete policy.enqueueport, since we don't know outport yet
                */
    			else if(policy.service == null && policy.queue != -1){         // compare to original condition without setting enqueueport since we add it when finding the path in QoSManager
    				status = "[*] Adding Policy: " + policy.name;//add service
        			//basic checks on validity
    				qos.addPolicy(policy);
    			}else if(checkIfServiceExists(policy.service, qos.getServices()) // not dealing with service yet
    					&& policy.enqueueport == -1 && policy.queue == -1){
    				status = "Adding Policy: " + policy.name;//add service
        			//basic checks on validity
    				qos.addPolicy(policy);
    			}else{status = "Service Policy or a Queuing Policy not defined. Check if Service Exists";}
    		}
    		else{
    			status = "Please enable Quality of Service";
    		}
    	}
    	return ("{\"status\" : \"" + status + "\"}");
    }
    
    @Delete
    public String delete(String qosJson) {
    	IQoSService qos = 
    			(IQoSService)getContext().getAttributes().
    			get(IQoSService.class.getCanonicalName());
    	
    	//dummy service
    	QoSPolicy policy;
    	
    	try{
    		policy = jsonToPolicy(qosJson);
    	}
    	catch(IOException e){
    		logger.debug("Error Parsing QoS Policy to JSON: {}, Error: {}", qosJson, e);
    		e.printStackTrace();
    		return "{\"status\" : \"Error! Could not parse policy, see log for details.\"}";
    	}
    	String status = null;
		if(qos.isEnabled()){
			boolean found = false;
			Iterator<QoSPolicy> sIter = qos.getPolicies().iterator();
			while(sIter.hasNext()){
				QoSPolicy p = sIter.next();
				if(p.policyid == policy.policyid){
					policy = p; //returned the entire policy
					found = true;
					break;
				}
			}
	
			if(!found){
				status = "Error! Cannot delete a rule with this ID or NAME, does not exist.";
				logger.error(status);
			}
			else{
				qos.deletePolicy(policy);
				status = "Type Of Service Service-ID: "+policy.policyid+" Deleted";
    			}
		}
		else{
			status = "Please enable Quality of Service";
		}
		return ("{\"status\" : \"" + status + "\"}");
    }
    

	
    /**
     * Turns POST json data into policy
     * @param pJson
     * @return
     * @throws IOException
     */
    public static QoSPolicy jsonToPolicy(String pJson) throws IOException{
		QoSPolicy policy = new QoSPolicy();
		//initialize needs json tools
		MappingJsonFactory jf = new MappingJsonFactory();
		JsonParser jp;
		
		try{
			jp = jf.createJsonParser(pJson);
		}catch(JsonParseException e){
			throw new IOException(e);
		}
    	JsonToken tkn = jp.getCurrentToken();
    	if(tkn != JsonToken.START_OBJECT){
    		jp.nextToken();
    		if(jp.getCurrentToken() != JsonToken.START_OBJECT){
    			logger.error("Did not recieve json start token, current " +
    					"token is: {}",jp.getCurrentToken());
    		}
    	}
    	while(jp.nextToken() != JsonToken.END_OBJECT){
    		if(jp.getCurrentToken() != JsonToken.FIELD_NAME){
    			throw new IOException("FIELD_NAME expected");
    		}
    		
    		try{
    			
    			String tmpS = jp.getCurrentName();
    			jp.nextToken();
    			
    			/** may be worth: jsonText = jp.getText(); to avoid over
    			 *  use of jp.getText() method call **/
    			
    			//get current text of the FIELD_NAME
    			logger.info("Current text is "+ jp.getText()); //debug for dev
    			if(jp.getText().equals("")){
    				//back to beginning of loop
    				continue;
    			}
    			if (tmpS == "policy-id" || tmpS == "sid" ){
					policy.policyid = Long.parseLong(jp.getText());
					//logger.info("[JSON PARSER]Policy Name: {}" , jp.getText());	
    			}
    			if (tmpS == "name"){
    				    if(jp.getText().equals("null"))  // for handle the case : policy from storage transfered by Json
    				    policy.name = null;
    				    else
    					policy.name = jp.getText();
    					//logger.info("[JSON PARSER]Policy Name: {}" , jp.getText());	
    			}
    			else if(tmpS == "protocol"){
    				// i.e "protocol": "6"
    				policy.protocol = Byte.parseByte(jp.getText());
    				//logger.info("[JSON PARSER]Policy Protocol: {}", jp.getText());	
    			}
    			else if(tmpS == "eth-type"  || tmpS == "ethtype"){
    				// i.e if "eth-type":"0x0800"
					if (jp.getText().startsWith("0x")) {
						policy.ethtype = U16.t(Integer.valueOf
								(jp.getText().replaceFirst("0x",""),16));
					}
					//return the short value of number i.e 8
					else{policy.ethtype = (short) Integer.parseInt(jp.getText());}
    				//logger.info("[JSON PARSER]Policy Eth-type: {}", jp.getText());	
    			}
    			else if(tmpS == "ingress-port" || tmpS == "ingressport"){
    				policy.ingressport = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Ingress-Port: {}", jp.getText());	
    			}
    			else if(tmpS == "ip-src" || tmpS == "ipsrc"){
    				if(tmpS== "ipsrc")
    					policy.ipsrc = Integer.parseInt(jp.getText()); // if get the policy from storage it has already tranfered to a integer
    				else
    			    policy.ipsrc = IPv4.toIPv4Address(jp.getText());
    				
    				//logger.info("[JSON PARSER]Policy IP-Src: {}", IPv4.fromIPv4Address(policy.ipsrc));
    			}
    			else if(tmpS == "ip-dst" || tmpS == "ipdst"){
    				if(tmpS== "ipdst")
    					policy.ipdst = Integer.parseInt(jp.getText()); // if get the policy from storage it has already tranfered to a integer
    				else
    			    policy.ipdst = IPv4.toIPv4Address(jp.getText());
    				//logger.info("[JSON PARSER]Policy IP-Dst: {}", IPv4.fromIPv4Address(policy.ipdst));		
    			}
    			else if(tmpS == "tos"){
    				//This is so you can enter a binary number or a integer number.
    				//It will be stored as a Byte
    				try{
    					//Try to get binary number first
    					Integer tmpInt = Integer.parseInt(jp.getText(),2);
    					policy.tos = tmpInt.byteValue();
    				}catch(NumberFormatException e){
    					//logger.debug("Number entered was not binary, processing as int...");
    					//Must be entered as 0-64
    					Integer tmpInt = Integer.parseInt(jp.getText());
    					policy.tos = tmpInt.byteValue();
    				}
    				//logger.info("[JSON PARSER]Policy TOS Bits: {}", jp.getText());
    			}
    			else if(tmpS == "vlan-id" || tmpS == "vlanid"){
    				policy.vlanid = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy VLAN-ID: {}", jp.getText());
    			}
    			else if(tmpS == "eth-src" || tmpS == "ethsrc"){
    				if(jp.getText().equals("null"))  //for handle the case : policy from storage transfered by Json
    					policy.ethsrc=null;
    				else
    				policy.ethsrc = jp.getText();
    				//logger.info("[JSON PARSER]Policy Eth-src: {}", jp.getText());
    			}
    			else if(tmpS == "eth-dst" || tmpS == "ethdst"){
    				if(jp.getText().equals("null")) //for handle the case : policy from storage transfered by Json
    					policy.ethdst=null;
    				else
    				policy.ethdst = jp.getText();
    				//logger.info("[JSON PARSER]Policy Eth-dst: {}", jp.getText());
    			}
    			else if(tmpS == "src-port" || tmpS == "tcpudpsrcport"){
    				policy.tcpudpsrcport = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Src-Port: {}", jp.getText());
    			}
    			else if(tmpS == "dst-port" || tmpS == "tcpudpdstport"){
    				policy.tcpudpdstport = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Dst-Port: {}", jp.getText());
    			}		
    			//TODO morph this to use a String[] of Switches
    			else if(tmpS == "sw"){
    				policy.sw = jp.getText();
    				//logger.info("[JSON PARSER]Policy Switch: {}", jp.getText());	
    			}
    			else if(tmpS == "queue"){
    				policy.queue = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy QUEUE: {}", jp.getText());
    			}
    			else if(tmpS == "enqueue-port" || tmpS == "enqueueport"){
    				policy.enqueueport = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy ENQUEUE-PORT: {}", jp.getText());
    			}
    			else if(tmpS == "service"){
    				if(jp.getText().equals("null"))//for handle the case : policy from storage transfered by Json
    					policy.service=null;
    				else
    				policy.service = jp.getText();
    				//logger.info("[JSON PARSER]Policy Service: {}", jp.getText());
    			}		
    			else if(tmpS == "priority"){
    				policy.priority = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Priority: {}", jp.getText());
    			}
    			
    		}catch(JsonParseException e){
    			logger.debug("Error getting current FIELD_NAME {}", e);
    		}catch(IOException e){
    			logger.debug("Error procession Json {}", e);
    		}
    		
    	}
    	return policy;
    }
    
    private static boolean checkIfPolicyExists(QoSPolicy policy,
			List<QoSPolicy> policies) {
		Iterator<QoSPolicy> pIter = policies.iterator();
		while(pIter.hasNext()){
			QoSPolicy p = pIter.next();
			if(policy.isSameAs(p) || policy.name.equals(p.name)){
				return true;
			}
		}
		return false;
	}
    private static boolean checkIfServiceExists(String service,
			List<QoSTypeOfService> services) {
		Iterator<QoSTypeOfService> sIter = services.iterator();
		while(sIter.hasNext()){
			QoSTypeOfService s = sIter.next();
			if(s.name.equals(service)){
				return true;
			}
		}
		return false;
	}


}
