package net.floodlightcontroller.QoSPolicyManager;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import net.floodlightcontroller.QoSManager.QoSManagerResource;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.qos.IQoSService;
import net.floodlightcontroller.qos.QoSPolicy;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.openflow.util.U16;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestrictionResource extends ServerResource {
	protected static Logger logger = LoggerFactory
			.getLogger(RestrictionResource.class);
	String status = null;

	@Get("json")
	public Object handleRequest() {
		IPolicyManager PM = (IPolicyManager) getContext().getAttributes().get(
				IPolicyManager.class.getCanonicalName());
		// gets the list of policies currently being implemented
		if(PM!=null)
		return PM.getPolicies();
		else return "error\n";
	}
	
	@Post
	public String add(String qosJson) {
		IPolicyManager PM = (IPolicyManager) getContext().getAttributes().get(
				IPolicyManager.class.getCanonicalName());
		PolicyRestriction restriction;
		try{
			restriction = jsonToRestriction(qosJson);
    	}
    	catch(IOException e){
    		logger.error("Error Parsing Restriction of Policy to JSON: {}, Error: {}", qosJson, e);
    		e.printStackTrace();
    		return "{\"status\" : \"Error! Could not parse policy, see log for details.\"}";
    	}
		String status = "add Restriction";
		//if(checkIfRestrictionExists(restriction,PM.getPolicies())){
    	//	status = "Error!, This policy already exists!";
    	//	logger.error(status);
    	//}
		//else{
			PM.addRestriction(restriction);
		
		//}
		
		return ("{\"status\" : \"" + status + "\"}");
	}
	
	
	public static PolicyRestriction jsonToRestriction(String rJson) throws IOException{
		PolicyRestriction restriction = new PolicyRestriction();
		//initialize needs json tools
		MappingJsonFactory jf = new MappingJsonFactory();
		JsonParser jp;
		
		try{
			jp = jf.createJsonParser(rJson);
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
    			if (tmpS == "restrictionid-id" || tmpS == "sid" ){
    				restriction.restrictionid = Long.parseLong(jp.getText());
					//logger.info("[JSON PARSER]Policy Name: {}" , jp.getText());	
    			}
    			if (tmpS == "name"){
    				    if(jp.getText().equals("null"))  // for handle the case : policy from storage transfered by Json
    				    	restriction.name = null;
    				    else
    				    	restriction.name = jp.getText();
    					//logger.info("[JSON PARSER]Policy Name: {}" , jp.getText());	
    			}
    			else if(tmpS == "protocol"){
    				// i.e "protocol": "6"
    				restriction.protocol = Byte.parseByte(jp.getText());
    				//logger.info("[JSON PARSER]Policy Protocol: {}", jp.getText());	
    			}
    			else if(tmpS == "eth-type"  || tmpS == "ethtype"){
    				// i.e if "eth-type":"0x0800"
					if (jp.getText().startsWith("0x")) {
						restriction.ethtype = U16.t(Integer.valueOf
								(jp.getText().replaceFirst("0x",""),16));
					}
					//return the short value of number i.e 8
					else{restriction.ethtype = (short) Integer.parseInt(jp.getText());}
    				//logger.info("[JSON PARSER]Policy Eth-type: {}", jp.getText());	
    			}
    			else if(tmpS == "tos"){
    				//This is so you can enter a binary number or a integer number.
    				//It will be stored as a Byte
    				try{
    					//Try to get binary number first
    					Integer tmpInt = Integer.parseInt(jp.getText(),2);
    					restriction.tos = tmpInt.byteValue();
    				}catch(NumberFormatException e){
    					//logger.debug("Number entered was not binary, processing as int...");
    					//Must be entered as 0-64
    					Integer tmpInt = Integer.parseInt(jp.getText());
    					restriction.tos = tmpInt.byteValue();
    				}
    				//logger.info("[JSON PARSER]Policy TOS Bits: {}", jp.getText());
    			}
    			else if(tmpS == "src-port" || tmpS == "tcpudpsrcport"){
    				restriction.tcpudpsrcport = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Src-Port: {}", jp.getText());
    			}
    			else if(tmpS == "dst-port" || tmpS == "tcpudpdstport"){
    				restriction.tcpudpdstport = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Dst-Port: {}", jp.getText());
    			}		
    			else if(tmpS == "queue"){
    				restriction.queue = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy QUEUE: {}", jp.getText());
    			}		
    			else if(tmpS == "priority"){
    				restriction.priority = Short.parseShort(jp.getText());
    				//logger.info("[JSON PARSER]Policy Priority: {}", jp.getText());
    			}
    			
    		}catch(JsonParseException e){
    			logger.debug("Error getting current FIELD_NAME {}", e);
    		}catch(IOException e){
    			logger.debug("Error procession Json {}", e);
    		}
    		
    	}
    	return restriction;
    }
	
	private static boolean checkIfRestrictionExists(PolicyRestriction restriction,
			List<PolicyRestriction> policies) {
		Iterator<PolicyRestriction> pIter = policies.iterator();
		while(pIter.hasNext()){
			PolicyRestriction p = pIter.next();
			if(restriction.isSameAs(p) || restriction.name.equals(p.name)){
				return true;
			}
		}
		return false;
	}
	
	
	
}

