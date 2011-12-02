package pl.nask.hsn.capture.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.mail.internet.InternetAddress;

/**
 * PROJECT: Capture-HPC
 * DATE: Apr 13, 2008
 * FILE: Preprocessor
 * COPYRIGHT HOLDER: Victoria University of Wellington, NZ
 * AUTHORS: Christian Seifert (christian.seifert@gmail.com)
 * <p/>
 * This file is part of Capture-HPC.
 * <p/>
 * Capture-HPC is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p/>
 * Capture-HPC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with Capture-HPC; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
public abstract class Preprocessor {
    private boolean hasMoreInputUrls;
    ServerSocket serverSocket;
    Socket clientSocket;
    ServerController sc;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Preprocessor.class);

    public Preprocessor() {

    }

    public void readInputUrls(final String port, String address) {
        logger.info("Opening server socket...");
        try {
            serverSocket = new ServerSocket(Integer.parseInt(port), 50, InetAddress.getByName(address));
            serverSocket.setSoTimeout(0);
        }
        catch (UnknownHostException e)
        {
            logger.error("Unknown host " + address);
            System.exit(-1);
        }
        catch (IOException e)
        {
            logger.error("IOException: " + e);
            System.exit(-1);
        }
        catch (Exception e) {
            logger.error("Error while opening socket. ");
            System.exit(-1);            
        }

        Thread fileTail = new Thread(new Runnable() {
            public void run() {

                while (true) {
                    try {
                        clientSocket = serverSocket.accept();
                        sc = new ServerController(clientSocket);
                        if (sc != null) {
                            Thread ServerControllerService = new Thread(sc);
                            ServerControllerService.start();
                        }
                    }
                    catch (Exception e) {
                        logger.error("Error while accepting.");
                    }
                }
            }
        });
        fileTail.setPriority(Thread.NORM_PRIORITY + 1);
        fileTail.start();
    }

    public void readInputUrlsFromFile(final String inputUrlsFile) {
        Thread fileTail = new Thread(new Runnable() {
            public void run() {
                try {
                    // TODO: prevent IOException

                    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inputUrlsFile), "UTF-8"));
                    while (true) {
                        try {
                            String line = in.readLine();
                            if (line != null) {
                                if ((line.length() > 0)) {
                                    line = line.trim();
                                    if (line.equalsIgnoreCase("end")) {
                                        logger.info(line);
                                        logger.info("Encountered end in input urls...stoppping processing input urls");
                                        hasMoreInputUrls = false;
                                        return;
                                    }

                                    if (!line.startsWith("#")) {
                                        preprocess(line);
                                    }
                                }
                            } else {
                                logger.info("Waiting for input URLs...");
                                try {
                                    Thread.sleep(60000);
                                } catch (InterruptedException e) {
                                }
                            }
                        } catch (IOException e) {
                            logger.error("Error reading input URLs...");
                            logger.error("Exception: ", e);
                        }
                    }
                    
                } catch (Exception e) {                    
                    logger.error("Exception: ", e);
                }
            }
        });
        fileTail.setPriority(Thread.NORM_PRIORITY + 1);
        fileTail.start();
    }


    /**
     * method that indicates whether the reading of the file completed.
     * if the preprocess function blocks this method would only indicate true once the preprocess function has completed.
     *
     * @return true if its done reading the input urls
     */
    public boolean hasMoreInputUrls() {
        return hasMoreInputUrls;
    }

    /* The meat of the preprocessor. Urls are passed from the input file to the preprocessor.
    * If URLs shall be processed by Capture, the preprocessor must call the addUrlToCaptureQueue function (which will not block). The preprocessor can tag ::<priority> to the URL to influence which URLs Capture will inspect first (0.0 for low and 1.0 for high priority)
    *
    * @param url - url as per capture format (optionally including prg and delay, for instance http://www.foo.com::iexplore::30)
    */
    abstract public void preprocess(String url);

    /* Sets the configuration of the preprocessor. Allows the preprocessor to be configured via the
    * existing config.xml configuration file.
    *
    * @param configuration - from the CDATA element of the preprocessor xml tag of config.xml
    */
    abstract public void setConfiguration(String configuration);


    /*
     * @param url - url as per capture format (including prg and delay)
     */
    public void addUrlToCaptureQueue(String url, String id) {

        Element e = new Element();
        e.name = "url";
        e.attributes.put("add", "");
        e.attributes.put("url", url);
        e.attributes.put("id", "-1");
        EventsController.getInstance().notifyEventObservers(e);
    }

    public int getCaptureQueueSize() {
        return UrlsController.getInstance().getQueueSize();
    }
}
