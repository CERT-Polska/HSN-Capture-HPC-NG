#!/usr/bin/env python

# Manage/revert script for ESX VMs. For use with Honey Spider Network Capture NG server.
# VMware vSphere CLI tools must be installed in PATH.

# Copyright (C) 2015 Schoenhofer Sales and Engineering GmbH
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

import subprocess
import sys
import time

# sys.argv will contain:
#   1: server address
#   2: username
#   3: password
#   4: path
#   5: guest username   (?)
#   6: guest password   (?)
#   7: guest command    (?)
#   8: guest command opt(?)


class ESXerror(Exception):
    def __init__(self, value):
        self.value = value

    def __str__(self):
        return repr(self.value)


class ESXobject(object):
    def __init__(self, address, username, password):
        self.address = str(address)
        self.username = str(username)
        self.password = str(password)

        self.basecommand = ['vmware-cmd',
                            '-H', self.address,
                            '-U', self.username,
                            '-P', self.password]

    def runcommand(self, *args):
        command_list = list(self.basecommand)
        for i in args:
            command_list.append(i)
        proc_vmware = subprocess.Popen(command_list, stdout=subprocess.PIPE)
        proc_vmware.wait()
        return proc_vmware.communicate()[0]


class ESXmachine(ESXobject):
    def __init__(self, address, username, password):
        super(ESXmachine, self).__init__(address, username, password)
        self.vms = [ESXvm(self, i) for i in self.getvmpaths()]

    def getvmpaths(self):
        vmpaths = self.runcommand('-l')
        return vmpaths.splitlines(False)[1:]

    def getvmbyname(self, name):
        name = str(name)
        for i in self.vms:
            if i.getname() == name:
                return i
        return None


class ESXvm(ESXobject):
    def __init__(self, host, vmpath):
        if not isinstance(host, ESXmachine):
            raise TypeError('Host must be an ESXmachine')

        super(ESXvm, self).__init__(host.address, host.username, host.password)
        self.host = host
        self.vmpath = str(vmpath)
        self.vmname = self.getname()
        self.basecommand.append(self.vmpath)

    def getname(self, path=None):
        if path is None:
            path = self.vmpath
        return path.split('/')[-1][0:-4]

    def isrunning(self):
        running = self.runcommand('getstate')[-3:-1]
        return running == 'on'

    def hassnapshot(self):
        snapshot = self.runcommand('hassnapshot')[-2:-1]
        return snapshot == '1'

    def revertsnapshot(self):
        if not self.hassnapshot():
            raise ESXerror('Machine has no snapshot to revert to')
        revert = self.runcommand('revertsnapshot')[-2:-1]
        return revert == '1'

    def start(self):
        startcmd = self.runcommand('start')[-2:-1]
        return startcmd == '1'

    def stop(self, hard=False):
        mode = 'soft'
        if hard:
            mode = 'hard'
        stopcmd = self.runcommand('stop', mode)
        return stopcmd[-2:-1] == '1'

    def __str__(self):
        return 'ESXvm: ' + self.getname()


def main():
    if len(sys.argv) <= 4:
        return 1
    esx = ESXmachine(str(sys.argv[1]),
                     str(sys.argv[2]),
                     str(sys.argv[3]))
    vm = esx.getvmbyname(str(sys.argv[4]))
    print('Selected {0}'.format(vm))

    if vm.isrunning():
        print('Stop VM. Return: {0}'.format(vm.stop(True)))

    while vm.isrunning():
        print('VM still running. Sleep.')
        time.sleep(2)

    try:
        print('Revert VM. Return: {0}'.format(vm.revertsnapshot()))
    except ESXerror as e:
        print(e)
        print('Starting VM. Return: {0}'.format(vm.start()))

    while not vm.isrunning():
        print('VM still not running. Sleep.')
        time.sleep(2)

    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(0)
