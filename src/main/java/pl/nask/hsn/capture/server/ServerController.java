/**
 * (C) Copyright 2008 NASK
 * Software Research & Development Department
 * Author: jaroslawj
 * Date: 2009-02-04
 */
package pl.nask.hsn.capture.server;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ServerController implements Runnable {

    private Socket clientSocket;
    private BufferedReader in;
    private String mesg;
    private static String defaultUrlID = "-1";
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ServerController.class);

    public ServerController(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        }
        catch (Exception e) {
            logger.error("Buffered reader creation exception.");
        }
    }

    public void run() {
        try {
            while ((mesg = in.readLine()) != null) {
                parse(mesg);
            }
        }
        catch (Exception e) {
            logger.error("Readline exception.");
            logger.error("Exception: ", e);
        }
    }

    private void parse(String mesg) {
        int pos = mesg.indexOf(' ');
        // two or more parts
        if (pos != -1) {
            String command = mesg.substring(0, pos);
            if (command != null && command.length() > 0) {
                if (command.equals("addurl")) {
                    String url;
                    String id = "-1";
                    if (mesg.lastIndexOf(' ') > mesg.indexOf(' ')) {
                        url = mesg.substring(mesg.indexOf(' ') + 1, mesg.lastIndexOf(' '));
                        id = mesg.substring(mesg.lastIndexOf(' ') + 1, mesg.length());

                    } else {
                        url = mesg.substring(mesg.indexOf(' ') + 1, mesg.length());
                    }
                    // addurl with predefined ID
                    if ((url != null && url.length() > 0)) {
                        addUrlToCaptureQueue(url, id);
                    }
                } else {
                    logger.warn("Unknown command.");
                }
            }
        }
        // one part message
        else {
            if (mesg.equals("exit")) {
                logger.info("Capture-server closed remotely.");
                System.exit(0);
            } else if (mesg.equals("reload")) {

                // clear previous entries
                ClientsController.getInstance().getExclusionLists().clear();
                // TODO: hardcoded filenames should be replaced
                reloadList("file", "FileMonitor.exl");
                reloadList("process", "ProcessMonitor.exl");
                reloadList("registry", "RegistryMonitor.exl");
                logger.info("Lists reloaded!");
            } else {
                logger.warn("Unknown command.");
            }

        }
    }

    // reload exclusion list
    private void reloadList(String monitor, String filename) {
        ExclusionList ex = new ExclusionList(monitor, filename);
        if (ex != null && ex.parseExclusionList()) {
            ClientsController.getInstance().getExclusionLists().put(monitor, ex);
        }

    }

    public void addUrlToCaptureQueue(String url, String id) {
        Element e = new Element();
        e.name = "url";
        e.attributes.put("add", "");
        e.attributes.put("url", url);
        e.attributes.put("id", id);
        EventsController.getInstance().notifyEventObservers(e);
    }
}
