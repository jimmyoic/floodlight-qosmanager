package net.floodlightcontroller.QoSManager;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class QoSManagerWebRoutable implements RestletRoutable{

	@Override
	public Restlet getRestlet(Context context) {
		// TODO Auto-generated method stub
		Router router = new Router(context);
		router.attach("/qos/json",QoSManagerResource.class);
		return router;
	}
	

	@Override
	public String basePath() {
		// TODO Auto-generated method stub
		return "/wm/manager";
	}

}
