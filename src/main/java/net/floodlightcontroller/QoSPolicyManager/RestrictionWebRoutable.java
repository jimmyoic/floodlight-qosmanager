package net.floodlightcontroller.QoSPolicyManager;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.QoSManager.QoSManagerResource;
import net.floodlightcontroller.restserver.RestletRoutable;

public class RestrictionWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		// TODO Auto-generated method stub
				Router router = new Router(context);
				router.attach("/restriction/json",RestrictionResource.class);
				return router;
	}

	@Override
	public String basePath() {
		// TODO Auto-generated method stub
		return "/wm/restriction";
	}

}
