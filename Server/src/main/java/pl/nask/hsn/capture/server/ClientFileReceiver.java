package pl.nask.hsn.capture.server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import sun.misc.BASE64Decoder;

public class ClientFileReceiver {

    private Client client;
    private String fileName;
    private int fileSize;
    private String fileType;
    private BufferedOutputStream outputFile;
    private int maxNameLen = 196;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ClientFileReceiver.class);

    public ClientFileReceiver(Client client, Element element) {
        this.client = client;
        fileName = element.attributes.get("name");
        fileSize = Integer.parseInt(element.attributes.get("size"));
        fileType = element.attributes.get("type");
        if (client != null) {
            if (client.getVisitingUrlGroup() != null) {
                int len = client.getVisitingUrlGroup().getGroupAsFileName().length();
                int cut = len > maxNameLen ? maxNameLen : len;
                fileName = "log" + File.separator + "changes" + File.separator + client.getVisitingUrlGroup().getGroupAsFileName().substring(0, len) + "." + fileType;
                if (fileType.equalsIgnoreCase("zip")) {
                    client.setZipName(fileName);
                }
            }
            try {
                File zipFile = new File(fileName);
                zipFile.createNewFile();
                zipFile.setReadable(true, false);
                outputFile = new BufferedOutputStream(new FileOutputStream(zipFile));
                this.client.send("<file-accept name=\"" + fileName + "\" />");
            } catch (FileNotFoundException e) {
                logger.error("Exception: ", e);
            } catch (IOException e) {
                logger.error("Exception: ", e);
            }
        }
    }

    public void receiveFilePart(Element element) {
        int partStart = Integer.parseInt(element.attributes.get("part-start"));
        int partEnd = Integer.parseInt(element.attributes.get("part-end"));
        int partSize = partEnd - partStart;
        String encoding = element.attributes.get("encoding");
        if (encoding.equals("base64")) {
            try {
                BASE64Decoder base64 = new BASE64Decoder();
                byte[] buffer = base64.decodeBuffer(element.data);
                if (buffer.length == partSize) {
                    outputFile.write(buffer, 0, partSize);
                } else {
                    logger.error("ClientFileReceiver: ERROR part size != decoded size - " + partSize + " != " + buffer.length);
                    logger.error(element.data);
                }
            } catch (IOException e) {
                logger.error("Exception: ", e);
            }
        } else {
            logger.error("ClientFileReceiver: encoding - " + encoding + " not supported");
        }
    }

    public void receiveFileEnd(Element element) {
        try {
            outputFile.flush();
            outputFile.close();
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }

    public String getFileName() {
        return fileName;
    }
}
