package pl.nask.hsn.capture.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

enum CLIENT_STATE {
    CONNECTING,
    CONNECTED,
    UPDATING,
    UPLOADING,
    WAITING,
    VISITING,
    AWAITING_VISITING,
    DISCONNECTED
}


/**
 * Manages a remote client allowing messages to be sent and received. Remote clients which
 * successfully connect to the server will have a virtual machine object associated with it.
 * To connect, a client must successfully send back to the server a connect event which
 * contains the unique id of both the virtual machine server which the virtual machine is
 * hosted on and the virtual machine. The client is not aware of what virtual machine server
 * is hosted on instead it interacts with the virtual machine instead. When the client needs
 * resetting for example after a malicious web-site has been visited the client simply sets the
 * state of the virtual machine to be needing resetting. The virtual machine server that manages
 * the virtual machine will independently detect this state change and act upon it.
 * <p>
 * A remote client has 4 states at which it can be in:
 * <ul>
 * <li>CONNECTED - Remote client has connected but no virtual machine mapped to it
 * <li>WAITING -
 * <li>VISITING
 * <li>DISCONNECTED
 * </ul>
 * <p>A remote client has 4 states. Connected is when the remote client has connected but has not
 * responded to the connect element. The state moves from being connected to waiting when the
 * remote client has responded to the connect event so that a virtual machine can be mapped to
 * the client. In this waiting state the client waits for a URL to be inserted into the queue.
 * Once a URL has been dequeued, sent, and a visiting event received the client will be put into
 * visiting state. When the remote client has finished visiting a URL the client moves to a waiting
 * state if the URL was classified as being benign or disconnect if it was malicious</p>
 * <p/>
 * <p><pre>Threads</pre>A client contains 2 threads. A ClientEventController which runs in the background and receives
 * messages from the remote client. And another which waits for URLs to be inserted into the URL
 * queue.</p>
 *
 * @author Ramon
 * @version 2.0
 * @see ClientEventController, VirtualMachine
 */
public class Client extends Observable implements Runnable, Comparable {
    private Socket clientSocket;
    private BufferedWriter clientSocketOutput;

    private UrlGroup visitingUrlGroup;
    private CLIENT_STATE clientState;
    private VirtualMachine virtualMachine;
    private Thread urlRetriever;
    private HashMap<String, ExclusionList> exclusionLists;
    private String algorithm;
    private StateChangeHandler stateChangeHandler;
    private String zipName;
    private boolean downloading;
    private Logger logger = Logger.getLogger(Client.class);


    public Client(HashMap<String, ExclusionList> exclusionLists) {
        if (stateChangeHandler == null) {
            stateChangeHandler = new StateChangeHandler();
        }

        this.exclusionLists = exclusionLists;
        clientState = CLIENT_STATE.CONNECTING;

        urlRetriever = new Thread(this, "ClientUrl");
        urlRetriever.start();
        downloading = false;


    }

    public void setVirtualMachine(VirtualMachine vm) {
        this.virtualMachine = vm;
    }

    public void setSocket(Socket c) {
        try {
            clientSocket = c;
            clientSocketOutput = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        } catch (IOException e) {
            logger.error("Exception: ", e);
            if (visitingUrlGroup != null) {
                visitingUrlGroup.setMajorErrorCode(ERROR_CODES.CAPTURE_CLIENT_CONNECTION_RESET.errorCode);
                visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.ERROR);
            }

            clientState = CLIENT_STATE.DISCONNECTED;
        }
    }

    public String errorCodeToString(long error) {
        for (ERROR_CODES e : ERROR_CODES.values()) {
            if (error == e.errorCode) {
                return e.toString();
            }
        }
        return "";
    }

    public boolean send(String message) {
        if (this.getClientState() != CLIENT_STATE.DISCONNECTED) {
            try {
                if (!message.endsWith("\0")) {
                    message += '\0';
                }
                clientSocketOutput.write(message);
                clientSocketOutput.flush();
                return true;
            } catch (IOException e) {
                this.setClientState(CLIENT_STATE.DISCONNECTED);
            }
        }
        return false;
    }

    public boolean sendXMLElement(Element e) {
        String msg = "<";
        msg += e.name;
        for (String key : e.attributes.keySet()) {
            msg += " ";
            msg += key;
            msg += "=\"";
            msg += e.attributes.get(key);
            msg += "\"";
        }
        msg += "/>";
        return this.send(msg);
    }

    public void reset() {
        if (this.getClientState() != CLIENT_STATE.VISITING) {
            this.setClientState(CLIENT_STATE.DISCONNECTED);
            this.downloading = false;
        }
    }

    public void disconnect() {

        if (virtualMachine != null) {

            virtualMachine.setClient(null);
            if (virtualMachine.getState() != VM_STATE.WAITING_TO_BE_REVERTED) 
                virtualMachine.setState(VM_STATE.WAITING_TO_BE_REVERTED);
        }
        if (visitingUrlGroup != null) {
            visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.ERROR);
        }
        try {
            this.downloading = false;
            clientSocketOutput.close();
            clientSocket.close();
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }

        finally {

            if (visitingUrlGroup != null) {
                if (!visitingUrlGroup.isMalicious()) {
                    visitingUrlGroup.setMalicious("dac", false, null); //sets underlying url malicious, which will cause error to be written clientSocketOutput down the line.
                }
                visitingUrlGroup.setMajorErrorCode(ERROR_CODES.CAPTURE_CLIENT_CONNECTION_RESET.errorCode);
                visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.ERROR);
            }

            if (getClientState() != CLIENT_STATE.DISCONNECTED) 
                setClientState(CLIENT_STATE.DISCONNECTED);
        }
        // repair zip if open
        if (downloading) {
            setDownloading(false);
            Zippa z = new Zippa();
            z.repair(zipName);

        }
    }

    public void parseEvent(Element element) {
        //only written to log if not (bulk and size of group is one
        if (this.getClientState() == CLIENT_STATE.VISITING) {
            String type = element.attributes.get("type");
            String time = element.attributes.get("time");
            String processId = element.attributes.get("processId");
            String process = element.attributes.get("process");
            String action = element.attributes.get("action");
            String object1 = element.attributes.get("object1");
            String object2 = element.attributes.get("object2");
            if (algorithm.equals("bulk") && visitingUrlGroup.size() > 1) {


                StateChange sc = null;
                boolean added = false;
                try {
                    sc = null;
                    if (type.equals("process")) {
                        sc = new ProcessStateChange(type, time, processId, process, action, object1, object2);
                    } else {
                        sc = new ObjectStateChange(type, time, processId, process, action, object1, object2);
                    }
                    added = stateChangeHandler.addStateChange(sc);
                    if (!added) {
                        logger.warn(virtualMachine.getLogHeader() + " - WARNING: Couldnt add state change " + sc.toCSV());
                    }
                } catch (ParseException e) {
                    logger.warn(virtualMachine.getLogHeader() + " - WARNING: Couldnt parse state change.");
                }
            } else if (algorithm.equals("dac") && visitingUrlGroup.size() > 1) {
                visitingUrlGroup.setMalicious("dac", true, null);
                //if we are visiting a group, we can reset right now,
                // because we will revisit URLs later to get more detail info about state changes
                logger.info(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + " MALICIOUS");

                visitingUrlGroup.writeEventToLog("\"" + type + "\",\"" + time +
                        "\",\"" + process + "\",\"" + action + "\",\"" + object1 + "\",\"" +
                        object2 + "\"\n");
                visitingUrlGroup.setVisitFinishTime(element.attributes.get("time"));

                List<Url> urls = visitingUrlGroup.getUrlList();
                for (Iterator<Url> urlIterator = urls.iterator(); urlIterator.hasNext();) {
                    Url url = urlIterator.next();
                    //url.setVisitFinishTime(element.attributes.get("time"));
                    url.setVisitFinishTime(new Date());
                }

                visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.VISITED);  //will set underlying url state
                visitingUrlGroup = null;
                this.setClientState(CLIENT_STATE.DISCONNECTED);
            } else { //must be seq or group size of 1
                visitingUrlGroup.setMalicious(algorithm, true, null);
                logger.info(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + " MALICIOUS");
                visitingUrlGroup.writeEventToLog("\"" + type + "\",\"" + time +
                        "\",\"" + process + "\",\"" + action + "\",\"" + object1 + "\",\"" +
                        object2 + "\"\n");
            }

        }
    }

    private void flushStateChangeHandler(Map<String, String> urlStateChangesMap) {
        visitingUrlGroup.writeEventToLog(urlStateChangesMap);
    }

    public void parseVisitEvent(Element element) {
        String type = element.attributes.get("type");
        algorithm = element.attributes.get("algorithm");
        if (type.equals("start")) {
            if (visitingUrlGroup == null) {
                logger.info("Visiting group is null");
            }
            if (this.getVirtualMachine() == null) {
                logger.info("vm is null");
            }
            logger.info(this.getVirtualMachine().getLogHeader() + " Visiting group " + visitingUrlGroup.getIdentifier());
            visitingUrlGroup.setVisitStartTime(element.attributes.get("time"));
            visitingUrlGroup.setAlgorithm(algorithm);

            //iterate through url elements and set startvisitTime & visiting state
            List<Element> items = element.childElements;
            for (Iterator<Element> iterator = items.iterator(); iterator.hasNext();) {
                Element item = iterator.next();
                String uri = item.attributes.get("url");
                Url url = visitingUrlGroup.getUrl(uri);
                //url.setVisitStartTime(item.attributes.get("time")); 
                url.setVisitStartTime(new Date());

            }
            visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.VISITING); //will set underlying url state


            this.setClientState(CLIENT_STATE.VISITING);
        } else if (type.equals("finish")) {
            visitingUrlGroup.setVisitFinishTime(element.attributes.get("time"));

            //iterate through url elements and set startvisitTime & visiting state
            Map<String, String> urlStateChangesMap = new TreeMap<String, String>();
            List<Element> items = element.childElements;
            List<Integer> processIds = new ArrayList<Integer>();
            for (Iterator<Element> iterator = items.iterator(); iterator.hasNext();) {
                Element item = iterator.next();
                String uri = item.attributes.get("url");
                Url url = visitingUrlGroup.getUrl(uri);
                int processId = Integer.parseInt(item.attributes.get("processId"));
                processIds.add(processId);
                urlStateChangesMap.put(url.getUrl().toString(), stateChangeHandler.getStateChangesCSV(processId));
                //url.setVisitFinishTime(item.attributes.get("time"));
                url.setVisitFinishTime(new Date());
                String urlMajorErrorCode = item.attributes.get("major-error-code");
                String urlMinorErrorCode = item.attributes.get("minor-error-code");
                if (!(urlMajorErrorCode.equals("0") || urlMajorErrorCode.equals(""))) {
                    url.setMajorErrorCode(Long.parseLong(urlMajorErrorCode));
                    url.setMinorErrorCode(Long.parseLong(urlMinorErrorCode));
                }
            }


            String malicious = element.attributes.get("malicious");
            if (visitingUrlGroup.isMalicious() || malicious.equals("1")) {
                logger.info(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + " MALICIOUS");
                if (visitingUrlGroup.size() > 1 && algorithm.equals("bulk")) {
                    if (!stateChangeHandler.getRemainderStateChangesCSV(processIds).equals("")) {
                        logger.info(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + " WARNING: No state changes were logged to url log file.");
                    }
                    flushStateChangeHandler(urlStateChangesMap);
                }

                visitingUrlGroup.setMalicious(algorithm, true, urlStateChangesMap);
                visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.VISITED);  //will set underlying url state
                visitingUrlGroup = null;
                this.setClientState(CLIENT_STATE.DISCONNECTED);
            } else {
                logger.info(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + " BENIGN");
                visitingUrlGroup.setMalicious(algorithm, false, urlStateChangesMap);
                visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.VISITED);  //will set underlying url state
                visitingUrlGroup = null;
                //JJM
                if (ConfigManager.getInstance().getConfigOption("revert-after-each-url").equals("true"))
                    this.setClientState(CLIENT_STATE.DISCONNECTED);
                else
                    this.setClientState(CLIENT_STATE.WAITING);
            }
        } else if (type.equals("error")) {
            String major = element.attributes.get("error-code");
            Map<String, String> urlStateChangesMap = new TreeMap<String, String>();
            List<Element> items = element.childElements;
            List<Integer> processIds = new ArrayList<Integer>();
            for (Iterator<Element> iterator = items.iterator(); iterator.hasNext();) {
                Element item = iterator.next();
                String uri = item.attributes.get("url");
                Url url = visitingUrlGroup.getUrl(uri);
                int processId = Integer.parseInt(item.attributes.get("processId"));
                processIds.add(processId);
                urlStateChangesMap.put(url.getUrl().toString(), stateChangeHandler.getStateChangesCSV(processId));
                //url.setVisitFinishTime(item.attributes.get("time"));
                url.setVisitFinishTime(new Date());
                String urlMajorErrorCode = item.attributes.get("major-error-code");
                String urlMinorErrorCode = item.attributes.get("minor-error-code");
                if (!urlMajorErrorCode.equals("")) {
                    url.setMajorErrorCode(Long.parseLong(urlMajorErrorCode));
                    url.setMinorErrorCode(Long.parseLong(urlMinorErrorCode));
                }
            }

            if (isErrorToRetry(major)) {
                logger.error(this.getVirtualMachine().getLogHeader() + " ERROR " + visitingUrlGroup.getIdentifier());

                if (ConfigManager.getInstance().getConfigOption("halt_on_revert") != null && ConfigManager.getInstance().getConfigOption("halt_on_revert").equals("true")) { //if option is set, vm is not reverted, but rather server is halted.
                    logger.info("Halt on revert set.");
                    logger.info("Revert called - exiting with code -20.");
                    System.exit(-20);
                }

                String malicious = element.attributes.get("malicious");
                if (malicious.equals("1")) {
                    if (visitingUrlGroup.size() > 1 && algorithm.equals("bulk")) {
                        if (!stateChangeHandler.getRemainderStateChangesCSV(processIds).equals("")) {
                            logger.info(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + " WARNING: No state changes were logged to url log file.");
                        }
                        flushStateChangeHandler(urlStateChangesMap);
                    }

                    visitingUrlGroup.setMalicious(algorithm, true, urlStateChangesMap);
                } else {
                    visitingUrlGroup.setMalicious(algorithm, false, urlStateChangesMap);
                }
                visitingUrlGroup.setMajorErrorCode(Long.parseLong(major));
                visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.ERROR);   //will set underlying url state and cause reset vm

                visitingUrlGroup = null;
                this.setClientState(CLIENT_STATE.DISCONNECTED);
            } else {
                //System.out.print(this.getVirtualMachine().getLogHeader() + " Visited group " + visitingUrlGroup.getIdentifier() + "\n");
                String malicious = element.attributes.get("malicious");
                if (malicious.equals("1")) {
                    flushStateChangeHandler(urlStateChangesMap);
                    visitingUrlGroup.setMalicious(algorithm, true, urlStateChangesMap);
                    visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.VISITED);   //will set underlying url state
                    logger.info(this.getVirtualMachine().getLogHeader() + " MALICIOUS " + visitingUrlGroup.getIdentifier());
                    visitingUrlGroup = null;
                    this.setClientState(CLIENT_STATE.DISCONNECTED);
                } else {
                    visitingUrlGroup.setMalicious(algorithm, false, urlStateChangesMap);
                    visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.VISITED);   //will set underlying url state and not cause reset vm
                    logger.info(this.getVirtualMachine().getLogHeader() + " BENIGN " + visitingUrlGroup.getIdentifier());
                    visitingUrlGroup = null;
                    
                    if (ConfigManager.getInstance().getConfigOption("revert-after-each-url").equals("true"))
                        this.setClientState(CLIENT_STATE.DISCONNECTED);
                    else
                        this.setClientState(CLIENT_STATE.WAITING);
                }
            }
        }
    }

    public boolean isErrorToRetry(String majorErrorCode) {
        if (majorErrorCode.equals("268435730") || majorErrorCode.equals("268435731") || majorErrorCode.equals("268436224") || majorErrorCode.equals("268435728")) {
            return false;
        } else {
            return true;
        }
    }

    public CLIENT_STATE getClientState() {
        return clientState;
    }

    // synchronized removed
    // added ClientVMLock to prevent deadlock detected in rev. 3028
    // there should be not concurrent calls for 

    public void setClientState(CLIENT_STATE newState) {
        synchronized (virtualMachine) {
            if (clientState == newState)
                return;
            clientState = newState;
            if (this.getVirtualMachine() != null) {
                logger.info(this.getVirtualMachine().getLogHeader() + " ClientSetState: " + newState.toString());
            } else {
                logger.info("[unknown server] ClientSetState: " + newState.toString());
            }
            if (clientState == CLIENT_STATE.WAITING) {
                virtualMachine.setState(VM_STATE.RUNNING);
                synchronized (urlRetriever) {
                    urlRetriever.notifyAll();
                }
            } else if (clientState == CLIENT_STATE.DISCONNECTED) {
                this.disconnect();
                synchronized (urlRetriever) {
                    urlRetriever.notifyAll(); //in order to let urlRetriever thread stop
                }
            } else if (clientState == CLIENT_STATE.CONNECTED) {
                send("<option name=\"capture-network-packets-malicious\" value=\"" +
                        ConfigManager.getInstance().getConfigOption("capture-network-packets-malicious") + "\"/>");
                send("<option name=\"capture-network-packets-benign\" value=\"" +
                        ConfigManager.getInstance().getConfigOption("capture-network-packets-benign") + "\"/>");
                send("<option name=\"collect-modified-files\" value=\"" +
                        ConfigManager.getInstance().getConfigOption("collect-modified-files") + "\"/>");
                if (ConfigManager.getInstance().getConfigOption("send-exclusion-lists").equals("true")) {
                    this.sendExclusionLists();
                }
            }
            this.setChanged();
            this.notifyObservers();
        }
    }


    private void sendExclusionLists() {
        logger.info("sending exclusion list elements...");
        for (ExclusionList ex : exclusionLists.values()) {
            for (Element e : ex.getExclusionListElements()) {
                sendXMLElement(e);
            }
        }
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public UrlGroup getVisitingUrlGroup() {
        return visitingUrlGroup;
    }

    public void setVisitingUrlGroup(UrlGroup visitingUrlGroup) {
        if (visitingUrlGroup == null) return;
        // new sending entry in log file, necessary for synchronization:
        SimpleDateFormat sf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.S");
        String date = sf.format(new Date());
        pl.nask.hsn.capture.server.Logger.getInstance().writeToLog(date + " " + this.getVirtualMachine().getServer().getAddress() + " T " + "SENDING " + visitingUrlGroup.getUrlList().get(0).getEffectiveId() + " " + visitingUrlGroup.getUrlList().get(0).getUrl());

        this.visitingUrlGroup = visitingUrlGroup;
        boolean success = this.send(this.visitingUrlGroup.toVisitEvent());

        if (this.getVirtualMachine().getServer().getAddress() != null)
            this.visitingUrlGroup.setProcessingIP(this.getVirtualMachine().getServer().getAddress());

        logger.info("Sending to: " + this.getVirtualMachine().getServer().getAddress());
        logger.info(this.getVirtualMachine().getLogHeader() + " Sending to visit group " + visitingUrlGroup.getIdentifier());

        if (!success) {
            logger.info("Sent URL to disconnected client");
            this.visitingUrlGroup.setMajorErrorCode(0x10000111);
            this.visitingUrlGroup.setUrlGroupState(URL_GROUP_STATE.ERROR);
        } else {
            clientState = CLIENT_STATE.AWAITING_VISITING; //dont change to set function call - could cause deadlock
        }
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public void run() {
        synchronized (urlRetriever) {
            while (clientState != CLIENT_STATE.DISCONNECTED) {
                try {
                    if (clientState == CLIENT_STATE.WAITING) {
                        this.setVisitingUrlGroup(UrlGroupsController.getInstance().takeUrlGroup());
                    }
                    urlRetriever.wait();
                } catch (InterruptedException e) {
                    logger.error("Exception: ", e);
                }
            }
        }
    }

    public int compareTo(Object o) {
        Client c1 = (Client) o;
        String vmId1 = c1.getVirtualMachine().getVmUniqueId();
        return vmId1.compareTo(getVirtualMachine().getVmUniqueId());
    }

    /**
     * Downloaded filename getter
     */
    public String getZipName() {
        return zipName;
    }

    /**
     * Downloaded filename setter
     */
    public void setZipName(String zipName) {
        this.zipName = zipName;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public void setDownloading(boolean downloading) {
        this.downloading = downloading;
    }
}
