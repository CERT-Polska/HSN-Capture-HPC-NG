#!/usr/bin/python
'''Usage: CaptureClientLoader.exe server [port]
'''

import time
import socket
import subprocess
import sys

try:
	server = sys.argv[1]
except IndexError:
	print >>sys.stderr, __doc__
	sys.exit(1)

try:
	port = int(sys.argv[2])
except IndexError:
	port = 7070

for x in range(1, 64):
	ip = socket.gethostbyname(socket.gethostname())
	if ip != '127.0.0.1':
		break
	time.sleep(x**2 * 0.001)
else:
	print 'Could not fetch IP address!'
	sys.exit(1)

print 'IP address:', ip

cmd = 'c:\progra~1\capture\CaptureClient.exe -s %s -p %d -a %s -b %s' % (server, port, ip, ip)

print cmd
sys.exit(subprocess.call(cmd.split()))
