package pl.nask.hsn.capture.server;

import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.io.*;
import java.text.SimpleDateFormat;


public class ESXServer implements VirtualMachineServer, Observer, Runnable {
    private String address;
    private int port;
    private String username;
    private String password;
    private String uniqueId;
    private static int REVERT_TIMEOUT = Integer.parseInt(ConfigManager.getInstance().getConfigOption("revert_timeout"));

    private LinkedList<VirtualMachine> virtualMachines;
    private LinkedBlockingDeque<WorkItem> queuedWorkItems;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(VMwareServer.class);

    public ESXServer(String address, int port, String username,
                        String password, String vmsid) {
        virtualMachines = new LinkedList<VirtualMachine>();
        queuedWorkItems = new LinkedBlockingDeque<WorkItem>();

        this.address = address;
        this.port = port;
        this.username = username;
        this.password = password;
        this.uniqueId = vmsid;
        Thread t = new Thread(this, "VMwareServer-" + address + ":" + port);
        t.start();

    }


    public boolean revertVirtualMachineStateAsync(VirtualMachine vm) {
        WorkItem item = new WorkItem("revert", vm);
        if (!queuedWorkItems.contains(item)) {
            queuedWorkItems.add(item);
        } else {
            logger.info(vm.getLogHeader() + " REVERT already in progress.");
        }
        return true;
    }

    public void addVirtualMachine(VirtualMachine vm) {
        vm.addObserver(this);
        logger.info("[" + address + ":" + port + "] VM added");
        virtualMachines.add(vm);
        vm.setState(VM_STATE.WAITING_TO_BE_REVERTED);
    }


    public void run() {
        String lastVM = "" + -1;
        while (true) {
            WorkItem item = queuedWorkItems.peek();

            if (item != null) {
                try {
                    if (item.function.equals("revert")) {
                        synchronized (item.vm) {
                            Stats.vmRevert++;
                            item.vm.setState(VM_STATE.REVERTING);

                            final String address = this.address;
                            final String username = this.username;
                            final String password = this.password;
                            final String vmPath = item.vm.getPath();
                            final String guestUsername = item.vm.getUsername();
                            final String guestPassword = item.vm.getPassword();
                            final String guestCmd = "cmd.exe";
                            final String cmdOptions = "/K " + item.vm.getCaptureClientPath() + " -s " + (String) ConfigManager.getInstance().getConfigOption("server-listen-address") + " -p " + (String) ConfigManager.getInstance().getConfigOption("server-listen-port") + " -a " + uniqueId + " -b " + item.vm.getVmUniqueId();

                            final String vmUniqueId = item.vm.getVmUniqueId();


                            String cmd = "";
                            if (System.getProperty("os.name", "Windows").toLowerCase().contains("windows")) {
                                cmd = "revert_esx.py";
                            } else {
                                cmd = "./revert_esx.py";
                            }
                            final String[] revertCmd = {cmd, address, username, password, vmPath};

                            //for(int i=0;i<revertCmd.length;i++)
                            //System.out.println(revertCmd[i]);

                            Date start = new Date(System.currentTimeMillis());

                            class VixThread extends Thread {
                                public int returnCode = 1;
                                Process vix = null;

                                public void run() {
                                    BufferedReader stdInput = null;
                                    try {
                                        vix = Runtime.getRuntime().exec(revertCmd);

                                        stdInput = new BufferedReader(new InputStreamReader(vix.getInputStream()));
                                        returnCode = vix.waitFor();
                                        String line = stdInput.readLine();
                                        while (line != null) {
                                            logger.info(line);
                                            line = stdInput.readLine();
                                        }
                                        stdInput.close();
                                    } catch (InterruptedException e) {
                                        returnCode = 17; //VIX_TIMEOUT
                                        if (vix != null) {
                                            logger.info("vix null");
                                            try {
                                                String line = stdInput.readLine();
                                                while (line != null) {
                                                    logger.info("line");
                                                    logger.info(line);
                                                    line = stdInput.readLine();
                                                }
                                                logger.info("line null");
                                            } catch (Exception ef) {
                                                logger.info(ef.getMessage());
                                                logger.error("Exception: ", e);
                                            }
                                            vix.destroy();
                                        }
                                        logger.error("[" + currentTime() + " " + address + ":" + port + "-" + vmUniqueId + "] Reverting VM timed out.");
                                    } catch (IOException e) {
                                        returnCode = 11; //VIX_ERROR
                                        if (vix != null) {
                                            try {
                                                String line = stdInput.readLine();
                                                while (line != null) {
                                                    logger.info(line);
                                                    line = stdInput.readLine();
                                                }
                                            } catch (Exception ef) {
                                                logger.error(ef.getMessage());
                                                logger.error("Exception: ", e);
                                            }
                                            vix.destroy();
                                        }
                                        logger.error("Exception: ", e);
                                        logger.error("[" + currentTime() + " " + address + ":" + port + "-" + vmUniqueId + "] Unable to access external Vix library.");
                                    }
                                }
                            }

                            VixThread vixThread = new VixThread();
                            vixThread.start();
                            for (int i = 0; i < REVERT_TIMEOUT; i++) {
                                if (vixThread.isAlive()) {
                                    Thread.currentThread().sleep(1000);
                                } else {
                                    i = REVERT_TIMEOUT; //vix completed before timeout was reached
                                }
                            }
                            if (vixThread.isAlive()) {
                                vixThread.interrupt();
                            }
                            vixThread.join(1000); //wait for a chance of for thread to finish

                            int error = vixThread.returnCode;


                            if (error == 0) {
                                item.vm.setLastContact(Calendar.getInstance().getTimeInMillis());
                                item.vm.setState(VM_STATE.RUNNING);
                                Date end = new Date(System.currentTimeMillis());
                                Stats.addRevertTimeTime(start, end);
                            } else {
                                logger.error("[" + currentTime() + " " + address + ":" + port + "-" + vmUniqueId + "] VMware error " + error);
                                item.vm.setState(VM_STATE.ERROR);
                            }

                            if (lastVM == item.vm.getVmUniqueId()) {
                                //identical VM. Occurs, for example if malicious URLs are encountered; dont slow things down much
                                logger.info("Reverting same VM...just waiting a bit");
                                Thread.sleep(1000 * Integer.parseInt(ConfigManager.getInstance().getConfigOption("same_vm_revert_delay")));
                            } else {
                                logger.info("Reverting different VM...waiting considerably");
                                //reverting different VMs (for instance during startup); this needs to be throttled considerably
                                Thread.sleep(1000 * Integer.parseInt(ConfigManager.getInstance().getConfigOption("different_vm_revert_delay")));
                            }
                            lastVM = item.vm.getVmUniqueId();
                        }
                    } else {
                        logger.error("Invalid work item " + item.function);
                    }
                } catch (Exception e) {
                    logger.error("Exception thrown in queue processor of VMware server " + e.toString());
                    logger.error("Exception: ", e);
                } finally {
                    queuedWorkItems.remove(item);
                }
                logger.info(item.vm.getLogHeader() + " Finished processing VM item: " + item.function);
            } else {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    logger.error("Exception: ", e);
                }
            }

        }
    }


    private String currentTime() {
        long current = System.currentTimeMillis();
        Date currentDate = new Date(current);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm:ss a");
        String strDate = sdf.format(currentDate);
        return strDate;
    }

    public void update(Observable arg0, Object arg1) {
        VirtualMachine vm = (VirtualMachine) arg0;
        if (vm.getState() == VM_STATE.WAITING_TO_BE_REVERTED) {
            revertVirtualMachineStateAsync(vm);
        }
    }

    public LinkedList<VirtualMachine> getVirtualMachines() {
        return virtualMachines;
    }

    public boolean isLocatedAt(String address, String port) {
        int iport = Integer.parseInt(port);
        return ((this.address.equals(address)) && (this.port == iport));
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getID() {
        return uniqueId;
    }

}
