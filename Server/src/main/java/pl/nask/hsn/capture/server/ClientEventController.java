package pl.nask.hsn.capture.server;

import java.io.*;
import java.net.SocketException;
import java.net.Socket;
import java.util.Calendar;
import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;
import org.apache.log4j.Logger;

public class ClientEventController extends DefaultHandler implements Runnable {

    private HashMap<String, ClientFileReceiver> clientFileReceivers;
    private Element currentElement;
    private Socket clientSocket;
    private Client client;
    private HashMap<String, ExclusionList> exclusionLists;
    private ClientsPinger clientsPinger;
    private org.apache.log4j.Logger logger = Logger.getLogger(ClientEventController.class);

    public ClientEventController(Socket clientSocket, HashMap<String, ExclusionList> exclusionLists, ClientsPinger clientsPinger) {
        this.clientSocket = clientSocket;
        this.exclusionLists = exclusionLists;
        currentElement = null;
        clientFileReceivers = new HashMap<String, ClientFileReceiver>();
        this.clientsPinger = clientsPinger;
        Thread receiver = new Thread(this, "ClientEC");
        receiver.start();


    }

    public void contactClient() {
        String message = "<connect server=\"2.5\" />";
        if (this.clientSocket.isConnected()) {
            try {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                if (!message.endsWith("\0")) {
                    message += '\0';
                }
                out.write(message);
                out.flush();
            } catch (IOException e) {
                logger.error("Exception: ", e);
            }
        }
    }

    public void parseConnectEvent(Element element) {
        String vmServerId = element.attributes.get("vm-server-id");
        String vmId = element.attributes.get("vm-id");
        if ((vmServerId != null && vmId != null)
                && (!vmServerId.equals("") && !vmId.equals(""))) {
            VirtualMachineServer vmServer = VirtualMachineServerController.getInstance().getVirtualMachineServer(vmServerId);
            String id = vmId;
            if (vmServer.getVirtualMachines() != null) {
                for (VirtualMachine vm : vmServer.getVirtualMachines()) {
                    if (vm.getVmUniqueId().equals(id)) {
                        client = new Client(exclusionLists);
                        client.setSocket(clientSocket);
                        client.setVirtualMachine(vm);
                        vm.setClient(client);
                        client.addObserver(clientsPinger);
                        client.setClientState(CLIENT_STATE.CONNECTED);
                        client.setClientState(CLIENT_STATE.WAITING);
                        break;
                    }
                }
            } else {
                logger.info("No machines defined!");
            }
        }
    }

    public void parseReconnectEvent(Element element) {
        String vmServerId = element.attributes.get("vm-server-id");
        String vmId = element.attributes.get("vm-id");
        if ((vmServerId != null && vmId != null)
                && (!vmServerId.equals("") && !vmId.equals(""))) {
            VirtualMachineServer vmServer = VirtualMachineServerController.getInstance().getVirtualMachineServer(vmServerId);
            String id = vmId;
            for (VirtualMachine vm : vmServer.getVirtualMachines()) {
                if (vm.getVmUniqueId().equals(id)) {
                    try {
                        client = vm.getClient();
                        if (client != null) {
                            client.getClientSocket().close();
                        }
                    } catch (IOException e) {
                        logger.error("Exception: ", e);
                    }
                    if (client != null) {
                        client.setSocket(clientSocket);
                    }
                    break;
                }
            }
        }
    }

    synchronized String myReadline(BufferedReader in) {
        String val = null;
        int c;
        try {
            while (true) {
                c = in.read();
                if (c == '\n' || c == -1) {
                    break;
                }
                if (val == null) {
                    val = "";
                }
                val += (char) c;

            }

            return val;
        } catch (IOException e) {
            return "Disconnected";
        }
        //return val;
    }

    public void run() {
        String buffer = null;

        try {

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            //while (!clientSocket.isClosed() && (in.readLine() != null)) {
            while (!clientSocket.isClosed() && ((buffer = myReadline(in)) != null)) {

                if (buffer.equals("Disconnected")) {
                    if (client != null) {

                        if (client.getVisitingUrlGroup() != null) {
                            client.getVisitingUrlGroup().setMajorErrorCode(ERROR_CODES.SOCKET_ERROR.errorCode);

                        }
                    }
                    break;
                }

                if (client != null && client.getVirtualMachine() != null) {
                    client.getVirtualMachine().setLastContact(Calendar.getInstance().getTimeInMillis());
                }


                if (buffer != null && buffer.length() > 0) {
                    XMLReader xr = XMLReaderFactory.createXMLReader();
                    xr.setContentHandler(this);
                    xr.setErrorHandler(this);
                    xr.parse(new InputSource(new StringReader(buffer)));
                }

            }


        } catch (SocketException e) {
            logger.error("Socket Exception");
            String where = "[unknown server]";
            if (client != null && client.getVirtualMachine() != null) {
                where = client.getVirtualMachine().getLogHeader();
                if (client.getVisitingUrlGroup() != null) {
                    client.getVisitingUrlGroup().setMajorErrorCode(ERROR_CODES.SOCKET_ERROR.errorCode);
                }
            }

            logger.info(where + " " + e.getMessage());



            logger.error("Exception: ", e);

        } catch (IOException e) {

            logger.error("IO Exception");
            String where = "[unknown server]";
            if (client != null && client.getVirtualMachine() != null) {
                where = client.getVirtualMachine().getLogHeader();
                if (client.getVisitingUrlGroup() != null) {
                    client.getVisitingUrlGroup().setMajorErrorCode(ERROR_CODES.SOCKET_ERROR.errorCode);
                }
            }
            logger.info(where + " " + e.getMessage());
            logger.error(where + "\nIOException: Buffer="
                    + buffer + "\n\n" + e.toString());
            logger.error("Exception: ", e);
        } catch (SAXException e) {
            logger.error("SAX Exception");
            String where = "[unknown server]";
            if (client != null && client.getVirtualMachine() != null) {
                where = client.getVirtualMachine().getLogHeader();
            }
            logger.info(where + " " + e.getMessage());
            logger.warn(where + "\nSAXException: Buffer="
                    + buffer + "\n\n" + e.toString());
            logger.error("Exception: ", e);
        } catch (Exception e) {
            logger.error("General Exception", e);
            String where = "[unknown server]";
            if (client != null && client.getVirtualMachine() != null) {
                where = client.getVirtualMachine().getLogHeader();
                if (client.getVisitingUrlGroup() != null) {
                    client.getVisitingUrlGroup().setMajorErrorCode(ERROR_CODES.SOCKET_ERROR.errorCode);
                }
            }
            //logger.info(where + " " + e.getMessage());
            logger.error(where + "\nException: Buffer="
                    + buffer + "\n\n", e);
        }

    }

    private Element constructElement(Element parent, String name, Attributes atts) {
        Element e = new Element();
        e.name = name;
        for (int i = 0; i < atts.getLength(); i++) {
            //System.out.println(atts.getLocalName(i) + " -> " + atts.getValue(i));
            e.attributes.put(atts.getLocalName(i), atts.getValue(i));
        }
        if (parent != null) {
            parent.childElements.add(e);
            e.parent = parent;
        }

        return e;
    }

    @Override
    public void startElement(String uri, String name,
            String qName, Attributes atts) {
        currentElement = this.constructElement(currentElement, name, atts);
    }

    @Override
    public void endElement(String uri, String name, String qName) {
        //long startTime = System.nanoTime();
        if (currentElement.parent == null) {
            if (name.equals("system-event")) {
                if (client != null) {
                    client.parseEvent(currentElement);
                }
            } else if (name.equals("connect")) {
                parseConnectEvent(currentElement);
            } else if (name.equals("reconnect")) {
                parseReconnectEvent(currentElement);
            } else if (name.equals("pong")) {
                String where = "[unknown server]";
                if (client != null && client.getVirtualMachine() != null) {
                    where = client.getVirtualMachine().getLogHeader();
                }
                logger.debug(where + " Got pong");                
            } else if (name.equals("visit-event")) {
                if (client != null) {
                    client.parseVisitEvent(currentElement);
                }
            } else if (name.equals("client")) {
            } else if (name.equals("file")) {
                String where = "[unknown server]";
                if (client != null && client.getVirtualMachine() != null) {
                    where = client.getVirtualMachine().getLogHeader();
                }
                logger.info(where + " Downloading file");
                client.setDownloading(true);
                ClientFileReceiver file = new ClientFileReceiver(client, currentElement);
                String fileName = currentElement.attributes.get("name");
                if (!clientFileReceivers.containsKey(fileName)) {
                    clientFileReceivers.put(fileName, file);
                } else {
                    logger.error("ClientFileReceiver: ERROR already downloading file - " + fileName + " " + client.getZipName());
                }
            } else if (name.equals("part")) {
                String fileName = currentElement.attributes.get("name");
                if (clientFileReceivers.containsKey(fileName)) {
                    ClientFileReceiver file = clientFileReceivers.get(fileName);
                    file.receiveFilePart(currentElement);
                    client.setDownloading(true);
                } else {
                    logger.error("ClientFileReceiver: ERROR receiver not found for file - " + fileName);
                    logger.error("Zip truncated!");
                }
            } else if (name.equals("file-finished")) {
                String where = "[unknown server]";
                if (client != null && client.getVirtualMachine() != null) {
                    where = client.getVirtualMachine().getLogHeader();
                }
                logger.info(where + " Finished downloading file");
                String fileName = currentElement.attributes.get("name");
                if (client != null) {
                    client.setDownloading(false);
                }
                if (clientFileReceivers.containsKey(fileName)) {
                    ClientFileReceiver file = clientFileReceivers.get(fileName);
                    file.receiveFileEnd(currentElement);
                    clientFileReceivers.remove(fileName);
                } else {
                    logger.error("ClientFileReceiver: ERROR receiver not found for file - " + fileName);
                }
            }
            currentElement = null;
        } else {
            currentElement = currentElement.parent;
        }
    }

    @Override
    public void characters(char ch[], int start, int length) {
        currentElement.data = new String(ch, start, length);
        currentElement.data = currentElement.data.trim();
    }
}
