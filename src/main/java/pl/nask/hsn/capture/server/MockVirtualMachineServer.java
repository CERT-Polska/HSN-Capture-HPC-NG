package pl.nask.hsn.capture.server;

import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Calendar;

public class MockVirtualMachineServer implements VirtualMachineServer, Observer {
    private LinkedList<VirtualMachine> virtualMachines;

    private String address;
    private int port;
    private String username;
    private String password;
    private String uniqueId;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(MockVirtualMachineServer.class);


    public MockVirtualMachineServer(String address, int port, String username,
                                    String password) {
        logger.info("Create Mock VM");

        virtualMachines = new LinkedList<VirtualMachine>();

        this.address = address;
        this.port = port;
        this.username = username;
        this.password = password;
        if (System.getProperty("fixIds") != null && System.getProperty("fixIds").equals("true")) {
            uniqueId = "" + 1;
        } else {
            uniqueId = "" + this.hashCode();
        }
    }

    public boolean revertVirtualMachineStateAsync(VirtualMachine vm) {
        WorkItem item = new WorkItem("revert", vm);
        item.vm.setState(VM_STATE.REVERTING);

        logger.info("Start Mock Client");
        String serverListenAddress = (String) ConfigManager.getInstance().getConfigOption("server-listen-address");
        MockClient client = new MockClient(serverListenAddress, uniqueId, item.vm.getVmUniqueId());
        client.connect();

        item.vm.setLastContact(Calendar.getInstance().getTimeInMillis());
        item.vm.setState(VM_STATE.RUNNING);
        return true;
    }

    public void addVirtualMachine(VirtualMachine vm) {
        vm.addObserver(this);
        logger.info("[" + address + ":" + port + "] VM added");
        virtualMachines.add(vm);
        vm.setState(VM_STATE.WAITING_TO_BE_REVERTED);
    }

    public LinkedList<VirtualMachine> getVirtualMachines() {
        return virtualMachines;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public boolean isLocatedAt(String address, String port) {
        int iport = Integer.parseInt(port);
        return ((this.address.equals(address)) && (this.port == iport));
    }

    public void update(Observable arg0, Object arg1) {
        VirtualMachine vm = (VirtualMachine) arg0;
        if (vm.getState() == VM_STATE.WAITING_TO_BE_REVERTED) {
            revertVirtualMachineStateAsync(vm);
        }
    }

    public String getID() {
        return uniqueId;
    }

}