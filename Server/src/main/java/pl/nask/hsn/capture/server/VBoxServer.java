/**
 * (C) Copyright 2008 NASK
 * Software Research & Development Department
 * Author: jaroslawj
 * Date: 2008-12-04
 */
package pl.nask.hsn.capture.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class VBoxServer implements VirtualMachineServer, Observer, Runnable {

    private String address;
    private int port;
    private String username;
    private String password;
    private String uniqueId;
    private static int REVERT_TIMEOUT = Integer.parseInt(ConfigManager.getInstance().getConfigOption("revert_timeout"));
    private LinkedList<VirtualMachine> virtualMachines;
    private LinkedBlockingDeque<WorkItem> queuedWorkItems;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(VBoxServer.class);

    public VBoxServer(String address, int port, String username,
            String password) {
        virtualMachines = new LinkedList<VirtualMachine>();
        queuedWorkItems = new LinkedBlockingDeque<WorkItem>();

        this.address = address;
        this.port = port;
        this.username = username;
        this.password = password;
        if (System.getProperty("fixIds") != null && System.getProperty("fixIds").equals("true")) {
            uniqueId = "" + 1;
        } else {
            uniqueId = "" + this.hashCode();
        }
        Thread t = new Thread(this, "VirtualBox-" + address + ":" + port);
        t.start();

    }

    public VBoxServer(String address, String vmsid) {
        virtualMachines = new LinkedList<VirtualMachine>();
        queuedWorkItems = new LinkedBlockingDeque<WorkItem>();
        this.address = address;
        this.port = 0;
        this.username = "";
        this.password = "";
        uniqueId = vmsid;
        Thread t = new Thread(this, "VirtualBox-" + address + ":" + port);
        t.start();
    }

    public VBoxServer(String address, int port, String username,
            String password, String vmsid) {
        virtualMachines = new LinkedList<VirtualMachine>();
        queuedWorkItems = new LinkedBlockingDeque<WorkItem>();

        this.address = address;
        this.port = port;
        this.username = username;
        this.password = password;
        uniqueId = vmsid;
        Thread t = new Thread(this, "VirtualBox-" + address + ":" + port);
        t.start();

    }

    public boolean revertVirtualMachineStateAsync(VirtualMachine vm) {
        WorkItem item = new WorkItem("revert", vm);
        if (!queuedWorkItems.contains(item)) {
            queuedWorkItems.add(item);
        } else {
            logger.warn(vm.getLogHeader() + " REVERT already in progress.");
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
                                cmd = "revert.exe";
                            } else {
                                cmd = "./revert";
                            }
                            final String[] revertCmd = {cmd, address, vmPath};

                            //for(int i=0;i<revertCmd.length;i++)
                            //System.out.println(revertCmd[i]);

                            Date start = new Date(System.currentTimeMillis());

                            class VixThread extends Thread {

                                public int returnCode = 1;
                                Process vix = null;

                                @Override
                                public void run() {

                                    try {
                                        InputStream errorStream, inputStream;
                                        OutputStream outputStream;
                                        vix = Runtime.getRuntime().exec(revertCmd);

                                        errorStream = vix.getErrorStream();
                                        inputStream = vix.getInputStream();
                                        outputStream = vix.getOutputStream();

                                        returnCode = vix.waitFor();

                                        // forced, experimental, not necessary
                                        if (errorStream != null)
                                        {
                                            errorStream.close();
                                        }
                                        
                                        if (inputStream != null)
                                        {
                                            inputStream.close();
                                        }

                                        if (outputStream != null)
                                        {
                                            outputStream.close();
                                        }

                                        if (vix != null)
                                            vix.destroy();

                                    } catch (InterruptedException e) {
                                        returnCode = 17;
                                        if (vix != null) {
                                            vix.destroy();
                                        }
                                        logger.warn("[" + currentTime() + " " + address + ":" + port + "-" + vmUniqueId + "] Reverting VM timed out.");
                                    } catch (IOException e) {
                                        if (vix != null) {
                                            vix.destroy();
                                        }
                                        logger.error("Execution exception ", e);
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
                    logger.error("Exception thrown in queue processor of VBox server " + e.toString());
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
        int iport = 0;
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
