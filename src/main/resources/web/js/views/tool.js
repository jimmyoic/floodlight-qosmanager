/*
   Copyright 2012 IBM & Marist College 2012

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
     function clear(){
        $("#ipsrc").val("");
        $("#ipdst").val("");
        $("#protocol").val("");
        $("#queue").val("");
        $("#priority").val("");
     }
     function reloadPolicy() {
        var tl = new ToolCollection();
        $('#content').html(new ToolListView({model:tl}).render().el);
        $('ul.nav > li').removeClass('active');
        $('li > a[href*="/tools"]').parent().addClass('active');      
     }
   

      var Create = {
      autoOpen: false,
      height: 300,
      width: 350,
      modal: true,
      buttons: {
        "Create a policy": function() {
        var ipSrc       = $("#ipsrc").val();
        var ipDst       = $("#ipdst").val();
        var protocol    = $("#protocol").val();
        var queue       = $("#queue").val();
        var priority    = $("#priority").val();
        var regIp = /([12]?\d{1,2}.){3}[12]?\d{1,2}/;
        var pass = true;
        if (!ipSrc.match(regIp) && !ipDst.match(regIp)) {
                alert("[IP] At least define one from IP Source or IP Destination!");
                pass=false;
             
        }
        if (!protocol.toLowerCase().match(/0x06|6|0x11|17/)) {
    		if (protocol.toLowerCase() == "tcp")
     			protocol = "6";
   			else if (protocol.toLowerCase() == "udp")
        		protocol = "17";
    		else {
        		alert("[Protocol] Protocol should be TCP or UDP!");
        		pass = false;
    		}
		}	
        if (!queue.match(/\d/)){
                
                if(queue === ""){
               
                queue = "0";
                }else{
                alert("[queue] please input a integer ");
                pass=false
                }
           }
       if(!priority.match(/\d/)){
            alert("[priority] please input a integer between 0 ~ 32767");
            pass = false;
       }
       else if (parseInt(priority) < 0 || parseInt(priority) > 32767) {
                alert("Priority should be 0~32767 !");
                pass = false;
        }
        
        if(pass == false) return;
         
        var name =       "ipSrc(" + ((ipSrc.match(regIp))?ipSrc:"-1") +
                                ")ipDst(" + ((ipDst.match(regIp))?ipDst:"-1") +
                                ")protocol(" + protocol +
                                ")priority(" + priority +
                                ")queue(" + queue +  ")";
       
        var sendInfo = '{"name":"'              + name          + '",' +
                                        '"ip-src":"'    + ipSrc         + '",' +
                                        '"ip-dst":"'    + ipDst         + '",' +
                                        '"protocol":"'  + protocol      + '",' +
                                        '"queue":"'     + queue         + '",' +
                                        '"priority":"'  + priority      + '",' +
                                        '"eth-type":"0x0800"}';
        
         $.ajax({
                type: 'POST',
                url: hackBase + "/wm/manager/qos/json",
                dataType: "json",
                data: sendInfo,
                async: false,
                success:function(msg) {
                        console.log(msg);
                        // reloadPolicy();
                        alert("Success!!!");
                },
        });
        
         alert("Success to add policy!");
         clear();
        $( this ).dialog( "close" );
       
        },
        Cancel: function() {
          $( this ).dialog( "close" );
           
        }
      },
      close: function() {
       clear();
      }
    };


      
        



window.ToolListView = Backbone.View.extend({

	initialize:function () {
			console.log('...Initializing Tools Page');
        	this.template = _.template(tpl.get('tools-list'));
        	this.model.bind("change", this.render, this);
        	this.model.bind("add", this.render, this);
        	
    	},


	render:function (eventName) {
      // console.log('render call lalala~~~~');
       		$(this.el).html(this.template({ntools:tl.length}));
			_.each(this.model.models, function (t) {
				$(this.el).find('#tools > tbody:first-child').append(new ToolsListItemView({model:t}).render().el);
			}, this);
			return this;
	},

});

window.ToolsListItemView = Backbone.View.extend({

	tagName:"tr",

	initialize:function () {
        this.template = _.template(tpl.get('tools-list-item'));
        this.model.bind("change", this.render, this);
        this.model.bind("destroy", this.render, this);
      },
      
    render:function () {
    	$(this.el).html(this.template(this.model.toJSON()));
        return this;
    },
    
});






window.ToolDetailsView = Backbone.View.extend({

	initialize:function () {
        this.template = _.template(tpl.get('tool'));
        this.model.bind("change", this.render, this);
        this.model.bind("add", this.render, this);
    },
    
    events:{
        "click #enable-button":"enableToolFunction",
        "click #disable-button":"disableToolFunction",
        "click #add-button":"addToolFunction",
        "click #remove-button":"removeToolFunction"
        
		//TODO add / remove qos policies
    },

    render:function (event) {
        var model = this.model;
        $(this.el).html(this.template(this.model.toJSON()));
        if (!event) var event = window.event;
    	if (event.target) targ = event.target;
    	if(targ.name != null){
        	//console.log(targ.name);
        	if(targ.name == "Quality" || targ.name == 'quality of service'){
            $(this.el).find('#qos').show();
            //Services & Policies
            $(this.el).find('#services').html(new ServiceListView({model:this.model.services}).render().el);
			     	$(this.el).find('#policies').html(new PolicyListView({model:this.model.policies}).render().el);
            setInterval(function () {
              console.log("policy update lala ~~");
              clearPolicy(model.policies);            
              // console.log("before leng: " + model.policies.length);
              getPolicies(model.policies);
              // console.log("after leng: " + model.policies.length);
            }, 3000);

      		}
			if(targ.name == "Firewall"){
    		console.log("Firewall specific load");
	  	}
		}
		return this;
    },
    
    enableToolFunction:function (event){
    	model = this.model;
    	if (!event) var event = window.event;
    	if (event.target) targ = event.target;
    	if(targ.name != null){
    		switch (targ.name.toLowerCase()){
    			case 'firewall':
    				//TODO send a post to enable
    				alert('Enabling: '+targ.name);
    				$.ajax({
  						type: 'GET',
 						url:hackBase + "/wm/firewall/module/enable/json",
  						async: false,
  						success:function (data){
  							console.log(data);
  							model.set({enabled: "true"});
  							},
  						error:function(data){
  							console.log(data);
  							},
						});
    				break;
    			case 'quality of service':
    				//TODO send a post to enable
    				alert('Enabling: '+targ.name);
    				$.ajax({
  						type: 'GET',
 						url:hackBase + "/wm/qos/tool/enable/json",
  						async: false,
  						success:function (data){
  							console.log(data);
  							model.set({enabled: "true"});
  							},
  						error:function(data){
  							console.log(data);
  							},
  						});
    				break;
    			default:
    				console.log("Cannot enable this tool");
    		}
    	}
    },
    disableToolFunction:function (event){
    	model = this.model;
    	if (!event) var event = window.event;
    	if (event.target) targ = event.target;
    	if(targ.name != null){
    		switch (targ.name.toLowerCase()){
    			case 'firewall':
    				//TODO send a post to disable
    				alert('Disabling: '+targ.name);
    				$.ajax({
  						type: 'GET',
 						url:hackBase + "/wm/firewall/module/disable/json",
  						async: false,
  						success:function (data){
  							console.log(data);
  							model.set({enabled: "false"});
  							},
  						error:function(data){
  							console.log(data);
  							},
						});
    				break;
    			case 'quality of service':
    				//TODO send a post to disable
    				alert('Disabling: '+targ.name);
    				$.ajax({
  						type: 'GET',
 						url:hackBase + "/wm/qos/tool/disable/json",
  						async: false,
  						success:function (data){
  							console.log(data);
  							model.set({enabled: "false"});
  							},
  						error:function(data){
  							console.log(data);
  							},
  						});
    				break;
    			default:
    				console.log("Cannot disable this tool");
    		}
    	}
    },
    
    addToolFunction:function(event){
         
         $( "#dialog-form" ).dialog(Create).dialog( "open" );
        
    },
    removeToolFunction:function(event){
    },
    
    
    
});


function getPolicies(plcs){

      console.log("Loading Policies..");
      var self = this;
        $.ajax({
            url:hackBase + "/wm/qos/policy/json",
            dataType:"json",
            success:function (data) {
               //console.log(data);
                _.each(data, function(p){
                  var policy = new Object();
                  policy.sid = p["policyid"]
                  policy.name = p["name"]
                  policy.ethtype = p["ethtype"]
                  policy.protocol = p["protocol"]
                  policy.ingressport = p["ingressport"]
                  policy.ipdst = p["ipdst"]
                  policy.ipsrc = p["ipsrc"]
                  policy.tos = p["tos"]
                  policy.vlanid = p["vlanid"]
                  policy.ethsrc = p["ethsrc"]
                  policy.ethdst = p["ethdst"]
                  policy.tcpudpdstport = p["tcpudpdstport"]
                  policy.tcpudpsrcport = p["tcpudpsrcport"]
                  policy.sw = p["sw"]
                  policy.queue = p["queue"]
                  policy.enqueueport = p["enqueueport"]
                  policy.service = p["service"]
                  policy.priority = p["priority"]
          
                  //console.log(p);
                  plcs.add({sid: policy.sid,
                        name: policy.name,
                        ethtype: policy.ethtype,
                        protocol: policy.protocol,
                        ingressport: policy.ingressport,
                        ipdst: policy.ipdst,
                        ipsrc: policy.ipsrc,
                        tos: policy.tos,
                        vlanid: policy.vlanid,
                        ethsrc: policy.ethsrc,
                        ethdst: policy.ethdst,
                        tcpudpdstport: policy.tcpudpdstport,
                        tcpudpsrcport: policy.tcpudpsrcport,
                        sw: policy.sw,
                        queue: policy.queue,
                        enqueueport: policy.enqueueport,
                        service: policy.service,
                        priority: policy.priority});
              });
            }
        });
}

function clearPolicy(plcs) {

  _.invoke(plcs.toArray(), 'destroy');

}

