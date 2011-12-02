package pl.nask.hsn.capture.server;

import org.xml.sax.SAXException;

import java.net.Socket;
import java.net.URLEncoder;
import java.net.BindException;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class MockClient implements Runnable {
    private String serverListenAddress;
    private String uniqueId;
    private String vmUniqueId;
    private DataOutputStream output = null;
    private List maliciousURLsURIEncoded = new ArrayList();
    private boolean sendFile = true;
    private String visitEndResponse;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(MockClient.class);

    public MockClient(String serverListenAddress, String uniqueId, String vmUniqueId) {
        this.serverListenAddress = serverListenAddress;
        this.uniqueId = uniqueId;
        this.vmUniqueId = vmUniqueId;

        try {
            maliciousURLsURIEncoded.add(URLEncoder.encode("http://www.google.fr", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            logger.error("Exception: ", e);
        }
    }

    public void connect() {
        Thread mockClientThread = new Thread(this, "MockClientThread");
        mockClientThread.start();
    }

    public void run() {
        logger.info("Client connecting ... ");

        Socket client = null;

        DataInputStream input = null;

        MockMsgParser parser = new MockMsgParser(this);

        try {
            client = new Socket(serverListenAddress, 7070);

            input = new DataInputStream(client.getInputStream());
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(input));
            output = new DataOutputStream(client.getOutputStream());

            while (client.isConnected()) {
                StringBuffer msg = new StringBuffer();
                char c = (char) inputReader.read();
                while (c != 0 && c != 65535 && client.isConnected()) {
                    msg.append(c);
                    c = (char) inputReader.read();
                }
                if (c == 0) {
                    parser.createEl(msg.toString());
                }
            }

        } catch (BindException e) {
            logger.error("Can't bind to socket. Some service is using desired port. Exiting.");
            System.exit(0);
        } catch (IOException e) {
            logger.error("Exception: ", e);
        } catch (SAXException e) {
            logger.error("Exception: ", e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException io) {
                    logger.error("Exception: ", io);
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException io) {
                    logger.error("Exception: ", io);
                }
            }
            if (client != null) {
                try {
                    client.close();
                } catch (IOException io) {
                    logger.error("Exception: ", io);
                }
            }
        }
    }

    public void handleConnectEvent(Element currentElement) {
        logger.info("Mock Client: Connect");
        String connectResponse = "<connect server=\"2.0\" vm-server-id=\"" + uniqueId + "\" vm-id=\"" + vmUniqueId + "\"/>\n";
        try {
            output.writeBytes(connectResponse);
            logger.info("Mock Client: Sent " + connectResponse);
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }

    public void handlePing() {
        logger.info("Mock Client: Ping");
        String pong = "<Pong/>\n";
        try {
            output.writeBytes(pong);
            logger.info("Mock Client: Sent " + pong);
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }


    public void handleVisitEvent(Element currentElement) {
        try {
            logger.info("Mock Client: Visit Event");

            String identifier = currentElement.attributes.get("identifier");
            String program = currentElement.attributes.get("program");
            int time = Integer.parseInt(currentElement.attributes.get("time"));

            DateFormat dfm = new SimpleDateFormat("d/M/yyyy H:m:s.S");
            //dfm.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));
            String startTime = dfm.format(new Date(System.currentTimeMillis()));

            String visitStartResponse = "<visit-event type=\"start\" time=\"" + startTime + "\" malicious=\"\" major-error-code=\"\" minor-error-code=\"\">";

            List items = currentElement.childElements;
            for (Iterator iterator = items.iterator(); iterator.hasNext();) {
                Element element = (Element) iterator.next();
                String url = element.attributes.get("url");
                String startTimeUrl = dfm.format(new Date(System.currentTimeMillis()));
                visitStartResponse += "<item url=\"" + url + "\" time=\"" + startTimeUrl + "\" major-error-code=\"\" minor-error-code=\"\"/>";
            }
            visitStartResponse += "</visit-event>\n";
            output.writeBytes(visitStartResponse);
            logger.info("Mock Client: Sent " + visitStartResponse);


            Thread.sleep(time * 1000);

            String endTime = dfm.format(new Date(System.currentTimeMillis()));

            items = currentElement.childElements;
            String visitedUrls = "";
            String malicious = "0";
            int i = 0;
            for (Iterator iterator = items.iterator(); iterator.hasNext();) {
                Element element = (Element) iterator.next();
                String url = element.attributes.get("url");
                if (maliciousURLsURIEncoded.contains(url)) {
                    malicious = "1";
                    //send a sample event change
                    String eventTime = dfm.format(new Date(System.currentTimeMillis()));
                    String sampleEvent = "<system-event time=\"" + eventTime + "\" type=\"file\" process=\"C:\\WINDOWS\\explorer.exe\" action=\"Write\" object=\"C:\\tmp\\TEST.exe\"/>\n";
                    output.writeBytes(sampleEvent);
                }
                String endTimeUrl = dfm.format(new Date(System.currentTimeMillis()));
                visitedUrls += "<item url=\"" + url + "\" time=\"" + endTimeUrl + "\" major-error-code=\"\" minor-error-code=\"\"/>";
            }

            //268435729
            visitEndResponse = "<visit-event type=\"finish\" time=\"" + endTime + "\" malicious=\"" + malicious + "\" major-error-code=\"\" minor-error-code=\"\">";
            visitEndResponse += visitedUrls;
            visitEndResponse += "</visit-event>\n";

            if (sendFile) {
                String fileSendRequest = "<file name=\"C:\\Program Files\\capture\\capture_2292007_1048.zip\" size=\"33\" type=\"zip\"/>\n";
                output.writeBytes(fileSendRequest);
                logger.info("Mock Client: Sent " + fileSendRequest);


            } else {


                output.writeBytes(visitEndResponse);
                logger.info("Mock Client: Sent " + visitEndResponse);
            }

        } catch (IOException e) {
            logger.error("Exception: ", e);

        } catch (InterruptedException e) {
            logger.error("Exception: ", e);
        }

    }

    public void handleFileEvent(Element currentElement) {
        try {
            String name = currentElement.attributes.get("name");
            logger.info("Mock Client: File Accept Msg " + name);

            String filePart = "<part name=\"C:\\Program Files\\capture\\capture_2292007_1048.zip\" part-start=\"0\" encoding=\"base64\" part-end=\"33\">evQ+Z0tTd3gsaCx64jGhulbMTF3QBzHAg4bmBCTNqD</part>\n";
            output.writeBytes(filePart);
            logger.info("Mock Client: Sent " + filePart);

            String fileFinish = "<file-finished name=\"C:\\Program Files\\capture\\capture_2292007_1048.zip\" size=\"33\"/>\n";
            output.writeBytes(fileFinish);
            logger.info("Mock Client: Sent " + fileFinish);

            output.writeBytes(visitEndResponse);
            logger.info("Mock Client: Sent " + visitEndResponse);
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }
}
