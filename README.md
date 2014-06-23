Floodlight-qosmanager
==========

a QoS-module modified from floodlight-qos-beta(Ryan.Wallner)

which can be found on the website of Floodlight

modified:

1.GUI for user to add and delete policy.
2.The tools View of GUI now shows only the policy in storage, so
  the "switch" attribute will always return null. We change the module
  which originally need destination and source to add the policy to
  that we can add policy only by destination, source , or both.

3.In floodlight-qos-beta, we add policy by qosmanager which use RESTAPI
  to add policies to both switch and storage. Now we change it that we 
  add policies only to the storage, when switch packetIn Controller will
  check if there is a match policy for that packet. If there it is, for example
  there is a policy:
	
	h1 -> -1 (none assigned, means to everywhere)
  
  and there is a packet sent by h1 to h3, which matches the policy.
  when switch gets the packet of h1, it will packet_in, and controller found
  that there is a match policy. so it will find the path of h1 to h3, and
  add the rule : "src=h1, dst=h3, queue=??" to each switch on the path.
  To make sure we can change the policy dynamically, we add a hard_time_out
  by adding a new row in StaticFlowPusher, so the switch will drop the rule
  periodically and packet_in again to check if there is an update in policy
  storage of controller

  you can change h3 to every hx in the example since the rule doesn't restrict
  the destination.

4.although we have GUI add and delete, we also design a new pythonAPI
  "fullmanager.py" to allow user add policy which only restrict src,dst,or both


floodlight-qos-beta
===================
The module we modified from

Contributor: Ryan Wallner (Ryan.Wallner1@marist.edu)
Floodlight with QoS module and tools to manage QoS state in an OF network

#after you clone run:

import to eclipse to run it
