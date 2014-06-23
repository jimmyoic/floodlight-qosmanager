#! /usr/bin/python
# coding: utf-8
'''
QoSPath.py v2---------------------------------------------------------------------------------------------------
Developed By: Ryan Wallner (ryan.wallner1@marist.edu)
Add QoS to a specific path in the network. Utilized circuit pusher developed by KC Wang
[Note]
	*the circuitpusher.py is needed in the same directory for this application to run
	 succesfully! This circuitpusher instance is used WITHOUT pushing statis flows. 
	 the static flows are commented out, circuitpusher is only used to get route.

	[author] - rjwallner
-----------------------------------------------------------------------------------------------------------------------
'''
'''
need to enable qos first

add new function:
	1.request 		(send policy to web routable)
	2.add_queue		(exec mininet-add-queues.py)

'''
'''
Usage:

	Add a Policy

	ip_src 		= 10.0.0.1
	ip_dst 		= 10.0.0.2
	protocol 	= 6
	priority 	= 32767
	queue 		= 2

	./full_manager.py -a -S 10.0.0.1 -D 10.0.0.2 -pro 6 -pri 32767 -q 2
	protocol : 6(TCP),1(ICMP),11(UDP) 

	no neet to input json and no need to mininet-add queue

'''
'''
modify by jimmyoic,kao,james1201
'''

import sys
import os
import re
import time
import simplejson #used to process policies and encode/decode requests
import subprocess #spawning subprocesses
import argparse
from qosmanager2 import httpHelper
'''
execute mininet-queue first
'''

def main():
	
	parser = argparse.ArgumentParser(description='QoS Full Manager')

	parser.add_argument('-a','--add',
										required=False,
										dest='action_op',
										action='store_const',
										const='add',
										metavar='add')
	parser.add_argument('-d','--delete',
										required=False,
										dest='action_op',
										action='store_const',
										const='delete',
										metavar='delete')
	parser.add_argument('-c','--controller',
										required=False,
										default='127.0.0.1',
										dest='controller',
										type=str,
										metavar='C')
	parser.add_argument('-p','--port',
										required=False,
										default='8080',
										type=str,
										dest='port',
										metavar='P')
	parser.add_argument('-S', '--ip-src',
										required=False,
										default='-1',
										type=str,
										dest='ip_src')
	parser.add_argument('-D', '--ip-dst',
										required=False,
										default='-1',
										type=str,
										dest='ip_dst')
	parser.add_argument('-pro', '--protocol',
										required=False,
										default='6',
										type=str,
										dest='protocol')
	parser.add_argument('-q', '--queue',
										required=False,
										default='0',
										type=str,
										dest='queue',
										metavar='Q')
	parser.add_argument('-pri', '--priority',
										required=False,
										default='16384',
										type=str,
										dest='priority')
	parser.add_argument('-J', '--json',
										required=False,
										dest='obj')
	
	

	args = parser.parse_args()


	# policy name not require now, ex-> src restrict only=> name =  src_10.0.0.1
	json = args.obj



	# the example input: 
	# ./qospath2.py –a –S 140.114.212.1 –D 140.114.212.3 –N sample –J \
	# ‘{“eth-type”:”0x0800”, “protocol”: “6”,”queue”: “2”}’

	'''
	Usage:

		Add a Policy

		ip_src 		= 10.0.0.1
		ip_dst 		= 10.0.0.2
		protocol 	= 6
		priority 	= 32767
		queue 		= 2

		./full_manager.py -a -S 10.0.0.1 -D 10.0.0.2 -pro 6 -pri 32767 -q 2
		protocol : 6(TCP),1(ICMP),11(UDP) 

	'''

	if args.action_op == 'add':
		print '[*] Add Policy'
		# set name: ipsrc+ipdst+protocol+priority+queue
		default_json = '{"name":"%s", "protocol":"%s", "eth-type":"0x0800", "priority":"%s", "queue":"%s"}' \
						% ('ipSrc(' + args.ip_src + ')ipDst(' + args.ip_dst + ')protocol(' + args.protocol + \
						')priority(' + args.priority + ')queue(' + args.queue + ')' \
						, args.protocol, args.priority, args.queue)
		l = simplejson.loads(default_json)

		if args.ip_src != '-1':
			l['ip-src'] = args.ip_src
		if args.ip_dst != '-1':
			l['ip-dst'] = args.ip_dst
		ipPattern = re.compile('\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}')
		if len(re.findall(ipPattern, args.ip_src)) == 0 and len(re.findall(ipPattern, args.ip_dst)) == 0:
			print 'At least define a src or dst'
			exit(1)
		if int(args.priority) < 0 or int(args.priority) > 32767:
			print 'Priority should be in range 0 ~ 32767'
			exit(1)
		if int(args.queue) < 0:
			print 'Queue should be >= 0'
			exit(1)
		request(simplejson.JSONEncoder(sort_keys=False,indent=3).encode(l), args.controller, args.port)
	elif args.action_op == 'delete':
		print '[*] Delete Policy'
		delete(args.name, args.controller, args.port)
	else:
		print '[*] Unrecognized Action'

	exit(0)



# Send add policy request to server
# @json  -Json object of the policy
# @controller, @p -Controller / Port

def request(json,controller,port):
	"""
	send policy to web routable
	"""
	print "Trying to add policy %s" % json
	"""
	the url is not correct yet!!!
	"""
	url = "http://%s:%s/wm/manager/qos/json" % (controller,port)
	try:
		helper = httpHelper(__name="QOS_Full_M")
		helper.connect(controller,port)
		req = helper.request("POST",url,json)
		print "[CONTROLLER]: %s" % req
		r_j = simplejson.loads(req)
		if r_j['status'] == "Please enable Quality of Service":
			print "[QoSPusher] please enable QoS on controller"
	except Exception as e:
		print e
		print "Could Not Complete Request"
		exit(1)
	else:
		print "Error parsing command %s" % type
		exit(1)
	helper.close_connection()


	

#Add a Quality of Service Path
# @NAME  -Name of the Path
# @SRC   -Source IP
# @DEST  -Destination IP
# @JSON  -Json object of the policy
# @C, @P -Controller / Port
# 
# Author- Ryan Wallner	
def add(name,src,dest,json,c,cprt):
	 print "Trying to create a circuit from host %s to host %s..." % (src,dest)
	 c_pusher = "circuitpusher.py"
	 qos_pusher = "qosmanager2.py"
	 pwd = os.getcwd()
	 print pwd
	 try:
		 if (os.path.exists("%s/%s" % (pwd,c_pusher))) and (os.path.exists("%s/%s" % (pwd,qos_pusher))):
			print "Necessary tools confirmed.. %s , %s" % (c_pusher,qos_pusher)
		 else:
			 print "%s/%s does not exist" %(pwd,c_pusher)
			 print "%s/%s does not exist" %(pwd,qos_pusher)
	 except ValueError as e:
		 print "Problem finding tools...%s , %s" % (c_pusher,qos_pusher)
		 print e
		 exit(1)
		
	 #first create the circuit and wait to json to pupulate  
	 print "create circuit!!!"
	 try:
		cmd = "--controller=%s:%s --type ip --src %s --dst %s --add --name %s" % (c,cprt,src,dest,name)
		print './circuitpusher.py %s' % cmd
		c_proc = subprocess.Popen('./circuitpusher.py %s' % cmd, shell=True)
		print "Process %s started to create circuit" % c_proc.pid
		#wait for the circuit to be created
		c_proc.wait()
	 except Exception as e:
		print "could not create circuit, Error: %s" % str(e)
	 
	 try:
		subprocess.Popen("cat circuits.json",shell=True).wait()
	 except Exception as e:
		print "Error opening file, Error: %s" % str(e)
		#cannot continue without file
		exit()
	 
	 print "Opening circuits.json in %s" % pwd
	 try:
		circs = "circuits.json"
		c_data = open(circs)
	 except Exception as e:
		print "Error opening file: %s" % str(e)
	 
	 print "Creating a QoSPath from host %s to host %s..." % (src,dest)
	 #Sleep purely for end user
	 time.sleep(3)
	 for line in c_data:
				data = simplejson.loads(line)
				if data['name'] != name:
					continue
				else:
					sw_id = data['Dpid']
					in_prt = data['inPort']
					out_prt = data['outPort']
					print"QoS applied to switch %s for circuit %s" % (sw_id,data['name'])
					print "%s: in:%s out:%s" % (sw_id,in_prt,out_prt)
					p = simplejson.loads(json)
					#add necessary match values to policy for path
					p['sw'] = sw_id
					p['name'] = name+"."+sw_id
					#screwed up connectivity on this match, remove
					#p['ingress-port'] = str(in_prt)
					p['ip-src'] = src
					p['ip-dst'] = dest
					keys = p.keys()
					l = len(keys)
					queue = False
					service = False
					for i in range(l):
						if keys[i] == 'queue':
							queue = True
						elif keys[i] == 'service':
							service = True
					
					if queue and service:
						polErr()
					elif queue and not service:
						p['enqueue-port'] = str(out_prt)
						pol = str(p)
						print "Adding Queueing Rule"
						sjson =  simplejson.JSONEncoder(sort_keys=False,indent=3).encode(p)
			                        print sjson
						cmd = "./qosmanager2.py --add --type policy --json '%s'   -c %s -p %s" % (sjson,c,cprt)
						p = subprocess.Popen(cmd, shell=True).wait()
					elif service and not queue:
						print "Adding Type of Service"
						sjson =  simplejson.JSONEncoder(sort_keys=False,indent=3).encode(p)
						print sjson
						cmd = "./qosmanager2.py --add --type policy --json '%s' -c %s -p %s" % (sjson,c,cprt)
						p = subprocess.Popen(cmd, shell=True).wait()
					else:
						polErr()
						
def polErr():
	print """Your policy is not defined right, check to 
make sure you have a service OR a queue defined"""
	
#Delete a Quality of Service Path
# @NAME  -Name of the Path
# @C, @P -Controller / Port
# 
# Author- Ryan Wallner  
def delete(name,c,p):
	print "Trying to delete QoSPath %s" % name
	# circuitpusher --controller {IP:REST_PORT} --delete --name {CIRCUIT_NAME}
	try:
		print "Deleting circuit"
		cmd = "./circuitpusher.py --controller %s:%s --delete --name %s" % (c,p,name)
		subprocess.Popen(cmd,shell=True).wait()
	except Exception as e:
		print "Error deleting circuit, Error: %s" % str(e)
		exit()
	
	qos_s = os.popen("./qosmanager2.py --list --type policies --controller %s --port %s" %(c,p)).read()
	#pull only the right info from response
	qos_s = qos_s[qos_s.find("[",qos_s.find("[")+1):qos_s.rfind("]")+1]
	data = simplejson.loads(qos_s)
	sjson = simplejson.JSONEncoder(sort_keys=False,indent=3).encode(data)
	jsond = simplejson.JSONDecoder().decode(sjson)
	#find policies that start with "<pathname>."
	l = len(jsond)
	for i in range(l):
		n = jsond[i]['name']
		if name in n:
			pol_id =  jsond[i]['policyid']
			try:
				cmd = "./qosmanager2.py --delete --type policy --json '{\"policy-id\":\"%s\"}' -c %s -p %s " % (pol_id,c,p)
				print cmd
				subprocess.Popen(cmd,shell=True).wait() 
			except Exception as e:
				print "Could not delete policy in path: %s" % str(e)

#Call main :)
if  __name__ == "__main__" :
	main()
