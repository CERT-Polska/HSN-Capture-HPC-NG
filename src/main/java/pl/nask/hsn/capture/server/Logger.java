package pl.nask.hsn.capture.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class Logger {

    private BufferedWriter outputLog;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Logger.class);

    private Logger() {
        boolean val;
        File logFile = null;
        String outputPath;
        try {
            File f = new File("log");
            if (!f.isDirectory()) {
                if (!(val = f.mkdir())) {
                    logger.error("Directory can't be created.");
                }
                f.setExecutable(true, false);
                f.setReadable(true, false);
            }
            File fc = new File("log" + File.separator + "changes");
            if (!fc.isDirectory()) {
                fc.mkdir();
                fc.setExecutable(true, false);
                fc.setReadable(true, false);
            }
            if ((outputPath = ConfigManager.getInstance().getConfigOption("output_path")) == null) {
                logFile = new File("log" + File.separator + "output.log");
            } else {
                logFile = new File(outputPath);
            }
            logFile.createNewFile();
            logFile.setReadable(true, false);
            outputLog = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile), "UTF-8"));
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }

    private static class LoggerHolder {

        private final static Logger instance = new Logger();
    }

    public static Logger getInstance() {
        return LoggerHolder.instance;
    }

    public synchronized void writeToLog(String text) {
        try {
            outputLog.write(text);
            outputLog.newLine();
            outputLog.flush();
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }
}
