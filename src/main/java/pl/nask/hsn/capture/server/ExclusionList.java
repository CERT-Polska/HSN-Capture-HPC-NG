package pl.nask.hsn.capture.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;

public class ExclusionList {
    private String monitor;
    private String file;
    private LinkedList<Element> exclusionElements;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ConfigManager.class);

    public ExclusionList(String monitor, String file) {
        this.monitor = monitor;
        this.file = file;
        exclusionElements = new LinkedList<Element>();
    }

    public boolean parseExclusionList() {
        boolean parsed = true;
        try {

            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line = "";
            int num = 0;
            while ((line = in.readLine()) != null) {
                num++;
                if (line.length() > 0 && !line.startsWith("#")) {
                    String[] tok = line.split("\t");
                    if (tok.length == 4) {
                        Element e = new Element();
                        e.name = monitor + "-exclusion";
                        e.attributes.put("excluded", tok[0]);
                        e.attributes.put("action", tok[1]);
                        e.attributes.put("subject", tok[2]);
                        e.attributes.put("object", tok[3]);
                        exclusionElements.add(e);
                    } else {
                        logger.warn("ExclusionList: WARNING Error in exclusion list, line " + num + " in " + file);
                    }
                }
            }
            in.close();
        } catch (FileNotFoundException e) {
            logger.error("ExclusionList: " + monitor + " - " + file + ": File not found");
            parsed = false;
        } catch (IOException e) {
            logger.error("Exception: ", e);
            parsed = false;
        }
        return parsed;
    }

    public LinkedList<Element> getExclusionListElements() {
        return exclusionElements;
    }
}
