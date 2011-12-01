package pl.nask.hsn.capture.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Observable;

enum URL_STATE {
    NONE,
    QUEUED,
    VISITING,
    VISITED,
    ERROR
}

public class Url extends Observable {

    private URI url;
    private long id;
    private String clientProgram;
    private int visitTime;
    private ERROR_CODES majorErrorCode = ERROR_CODES.OK;
    private long minorErrorCode;
    private Boolean malicious;
    private URL_STATE urlState;
    private Date visitStartTime;
    private Date visitFinishTime;
    private String logFileDate;
    private Date firstStateChange;

    private int retryCount;
    private BufferedWriter logFile;
    private long groupID;
    private boolean initialGroup;
    private String processingIP;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Url.class);

    //0.0 low
    //1.0 high
    public double getPriority() {
        return priority;
    }

    private double priority;


    public Url(String u, String cProgram, int vTime, double priority, long id) throws URISyntaxException {
        url = new URI(u);
        this.priority = priority;
        malicious = null;
        if (cProgram == null || cProgram.equals("")) {
            clientProgram = ConfigManager.getInstance().getConfigOption("client-default");
        } else {
            clientProgram = cProgram;
        }
        visitTime = vTime;
        urlState = URL_STATE.NONE;
        this.id = id;
        retryCount = 0;
    }



    //JJA
    public void setVisitStartTime(Date visitStartTime) {
        this.visitStartTime = visitStartTime;
    }

    public void setGroupId(long groupID) {
        this.groupID = groupID;
    }

    public String getVisitStartTime() {
        SimpleDateFormat sf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.S");
        return sf.format(visitStartTime);
    }


    public void setVisitFinishTime(Date visitFinishTime) {
        this.visitFinishTime = visitFinishTime;
    }


    public String getVisitFinishTime() {
        SimpleDateFormat sf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.S");
        return sf.format(new Date());
    }

    private String getLogfileDate(long time) {
        SimpleDateFormat sf = new SimpleDateFormat("ddMMyyyy_HHmmss");
        return sf.format(new Date(time));
    }

    public void writeEventToLog(String event) {
        try {
            if (logFile == null) {
                String logFileName = filenameEscape(getEffectiveId() + "_" + this.getLogfileDate(visitStartTime.getTime()));

                File log = new File("log" + File.separator + "changes" + File.separator + logFileName + ".log");
                log.createNewFile();
                log.setReadable(true, false);                
                logFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log), "UTF-8"));

            }

            if (firstStateChange == null) {
                String[] result = event.split("\",\"");
                String firstStateChangeStr = result[1];
                SimpleDateFormat sf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.S");
                firstStateChange = sf.parse(firstStateChangeStr);
            }

            setMalicious(true);
            logFile.write(event);
            logFile.flush();
        } catch (UnsupportedEncodingException e) {
            logger.error("Exception: ", e);
        } catch (IOException e) {
            logger.error("Exception: ", e);
        } catch (ParseException e) {
            logger.error("Exception: ", e);
        }
    }

    
    public void syncEventLog() {
        try {
            if (logFile != null) {
                logFile.flush();
            }
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }

    public void closeEventLog() {
        try {
            if (logFile != null) {
                logFile.close();
                logFile = null; 
            }
        } catch (IOException e) {
            logger.error("Exception: ", e);
        }
    }

    public boolean isMalicious() {
        return malicious;
    }

    public void setMalicious(boolean malicious) {
        this.malicious = malicious;
    }

    public URL_STATE getUrlState() {
        return urlState;
    }

    public synchronized void setUrlState(URL_STATE newState) {
        if (urlState == newState)
            return;

        urlState = newState;
        //System.out.println("\tUrlSetState: " + newState.toString());
        if (urlState == URL_STATE.VISITING) {
            String date = getVisitStartTime();
            Stats.visiting++;
            Logger.getInstance().writeToLog(date + " " + this.getProcessingIP() + " T " + "VISITING " + getEffectiveId() + " " + url);
        } else if (urlState == URL_STATE.VISITED) {
            String date = getVisitFinishTime();
            Logger.getInstance().writeToLog(date + " " + this.getProcessingIP() + " T " + "VISITED " + getEffectiveId() + " " + url);
            Stats.visited++;
            Stats.addUrlVisitingTime(visitStartTime, visitFinishTime, visitTime);
            if (this.malicious != null && this.malicious) {
                String retryLabel = " F ";
                Stats.malicious++;
                Stats.addFirstStateChangeTime(firstStateChange, visitFinishTime, visitTime);
                // retry tags
                if (this.getMajorErrorCode() == ERROR_CODES.OK ||
                        this.getMajorErrorCode() == ERROR_CODES.TIMEOUT_ERROR ||
                        this.getMajorErrorCode() == ERROR_CODES.VISITATION_MULTIPLE_ERRORS ||
                        this.getMajorErrorCode() == ERROR_CODES.VISITATION_WARNING ||
                        this.getMajorErrorCode() == ERROR_CODES.NETWORK_ERROR || retryCount > 0) {
                    retryLabel = " F ";
                } else {
                    retryLabel = " T ";
                }
                Logger.getInstance().writeToLog(date + " " + this.getProcessingIP() + retryLabel + "MALICIOUS " + getEffectiveId() + " " + url);
            } else {
                Stats.safe++;
                Logger.getInstance().writeToLog(date + " " + this.getProcessingIP() + " F " + "BENIGN " + getEffectiveId() + " " + url);
            }
            closeEventLog();

        } else if (urlState == URL_STATE.ERROR) {
            String retryLabel;
            // retry tags
            if (this.getMajorErrorCode() == ERROR_CODES.OK ||
                    this.getMajorErrorCode() == ERROR_CODES.TIMEOUT_ERROR ||
                    this.getMajorErrorCode() == ERROR_CODES.VISITATION_MULTIPLE_ERRORS ||
                    this.getMajorErrorCode() == ERROR_CODES.VISITATION_WARNING ||
                    this.getMajorErrorCode() == ERROR_CODES.NETWORK_ERROR || retryCount > 0) {
                retryLabel = " F ";
            } else {
                retryLabel = " T ";
            }
            if (this.malicious != null) {
                if (this.malicious) {

                    Stats.malicious++;
                    SimpleDateFormat sf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.S");
                    String date = sf.format(new Date());
                    if (firstStateChange != null && visitFinishTime != null) {//could happen when VM is stalled. since the tim can't be calculated correctly, we skip this one for this URL
                        Stats.addFirstStateChangeTime(firstStateChange, visitFinishTime, visitTime);
                    }
                    Logger.getInstance().writeToLog(date + " " + this.getProcessingIP() + retryLabel + "MALICIOUS " + getEffectiveId() + " " + url);
                } else {
                    String finishDate = getVisitFinishTime();
                    Stats.urlError++;
                    Logger.getInstance().writeToLog(finishDate + " " + this.getProcessingIP() + retryLabel + majorErrorCode + "-" + minorErrorCode + " " + getEffectiveId() + " " + url);
                }
            }
            closeEventLog();
        }
        this.setChanged();
        this.notifyObservers();
    }

    public int getVisitTime() {
        return visitTime;
    }

    public String getClientProgram() {
        return clientProgram;
    }

    public String getEscapedUrl() {
        try {
            return URLEncoder.encode(url.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("Exception: ", e);
            return "";
        }
    }

    public String getUrl() {
        return url.toString();
    }

    private String filenameEscape(String text) {
        String escaped;
        escaped = text;
        escaped = escaped.replaceAll("\\\\", "%5C");
        escaped = escaped.replaceAll("/", "%2F");
        escaped = escaped.replaceAll(":", "%3A");
        escaped = escaped.replaceAll("\\?", "%3F");
        escaped = escaped.replaceAll("\"", "%22");
        escaped = escaped.replaceAll("\\*", "%2A");
        escaped = escaped.replaceAll("<", "%3C");
        escaped = escaped.replaceAll(">", "%3E");
        escaped = escaped.replaceAll("\\|", "%7C");
        escaped = escaped.replaceAll("&", "%26");
        return escaped;
    }

    public String getUrlAsFileName() {
        //return this.filenameEscape(this.getUrl()) + "_" + this.getLogfileDate(visitStartTime.getTime());
        return this.filenameEscape(getEffectiveId() + "_" + this.getLogfileDate(visitStartTime.getTime()));
    }

    public ERROR_CODES getMajorErrorCode() {
        if (majorErrorCode == null) {
            return ERROR_CODES.OK;
        }
        return majorErrorCode;
    }

    public void setMajorErrorCode(long majorErrorCode) {
        boolean validErrorCode = false;

        for (ERROR_CODES e : ERROR_CODES.values()) {
            if (majorErrorCode == e.errorCode) {
                validErrorCode = true;
                this.majorErrorCode = e;
            }
        }

        if (!validErrorCode) {
            logger.error("Received invalid error code from client " + majorErrorCode);
            this.majorErrorCode = ERROR_CODES.INVALID_ERROR_CODE_FROM_CLIENT;
        }

    }

    public long getMinorErrorCode() {
        return minorErrorCode;
    }

    public void setMinorErrorCode(long minorErrorCode) {
        this.minorErrorCode = minorErrorCode;
    }

    public long getGroupID() {
        return groupID;
    }

    public void setInitialGroup(boolean initialGroup) {
        this.initialGroup = initialGroup;
    }

    public boolean isInitialGroup() {
        return initialGroup;
    }


    public String getProcessingIP() {
        return processingIP;
    }

    public void setProcessingIP(String processingIP) {
        this.processingIP = processingIP;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getEffectiveId() {
        long effectiveID = (id != -1) ? id : groupID;
        return effectiveID;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
