/*
 * (C) NASK, GOVCERT.NL, SURFnet
 * author: jaroslawj NASK 2009-10-27
 */

package pl.nask.hsn.capture.server;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;

public class Zippa {
    int count = 0;
    int lastOffset = 0;
    List<String> fnames = new ArrayList<String>();
    List<LocalFileEntry> lfes = new ArrayList<LocalFileEntry>();
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Zippa.class);

    public int readBytes(byte data[], int offset, int count) {
        int sum = 0;
        int shift = 0;
        for (int i = offset; i < offset + count; ++i) {
            sum += (data[i] & 0xff) << shift;
            shift += 8;
        }
        return sum;
    }

    public byte[] readBytesAsVector(byte data[], int offset, int count) {
        return Arrays.copyOfRange(data, offset, count);
    }

    public void repairZipFile(String fname, byte data[], int offset, List<LocalFileEntry> lfes) {

        byte[] lfb = null;
        lfb = Arrays.copyOfRange(data, 0, offset);
        List<CentralDirectoryEntry> cdes = new ArrayList<CentralDirectoryEntry>();
        // restore cde
        for (LocalFileEntry lfe : lfes) {
            CentralDirectoryEntry cde = new CentralDirectoryEntry(lfe);
            cdes.add(cde);
        }
        File f = new File(fname + "a");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(lfb, 0, offset);
            // store cde
            int cdelen = 0;
            for (CentralDirectoryEntry cde : cdes) {
                fos.write(cde.getID(), 0, 4);
                fos.write(cde.getVersion(), 0, 1);
                fos.write(cde.getHostos(), 0, 1);
                fos.write(cde.getVersion(), 0, 1);
                fos.write(cde.getHostos(), 0, 1);
                fos.write(cde.getGpbf(), 0, 2);
                fos.write(cde.getCompMethod(), 0, 2);
                fos.write(cde.getTimestamp(), 0, 4);
                fos.write(cde.getCrc32(), 0, 4);
                fos.write(cde.getCfsize(), 0, 4);
                fos.write(cde.getUcfsize(), 0, 4);
                fos.write(cde.getFnamelen(), 0, 2);
                fos.write(cde.getFxtrlen(), 0, 2);
                fos.write(cde.getFcmtlen(), 0, 2);
                fos.write(cde.getDisknumber(), 0, 2);
                fos.write(cde.getAttrs(), 0, 2);
                fos.write(cde.getExtattrs(), 0, 4);
                fos.write(cde.getReloffset(), 0, 4);
                fos.write(cde.getFname(), 0, cde.getFname().length);
                cdelen += cde.getFname().length + 46;
            }

            CentralDirectoryEndEntry cdend = new CentralDirectoryEndEntry(cdes.size(), cdes.size(), cdelen, offset);
            fos.write(cdend.getID(), 0, 4);
            fos.write(cdend.getIdx(), 0, 2);
            fos.write(cdend.getDidx(), 0, 2);
            fos.write(cdend.getDirentries(), 0, 2);
            fos.write(cdend.getDirentries(), 0, 2);
            fos.write(cdend.getDirsize(), 0, 4);
            fos.write(cdend.getDiroffset(), 0, 4);
            fos.write(cdend.getFcmtlen(), 0, 2);
            
            fos.write(cdend.getCmt(), 0, 8); 
            fos.close();
        } catch (FileNotFoundException fnfe) {
            logger.error("Exception: ", fnfe);
        }
        catch (IOException ioe) {
            logger.error("Exception: ", ioe);
        }
        finally {

        }


    }

    public void processZipFile(String arg, byte[] data, int offset) {

        count++;

        lastOffset = offset;
        // check if broken id
        if (offset + 4 <= data.length) {
            int id = readBytes(data, offset, 4);
            if (id == 0x4034B50) {
                if (offset + 30 > data.length) {
                    repairZipFile(arg, data, lastOffset, lfes);
                }

                offset += 4;
                int version = readBytes(data, offset, 2);
                // skip gpb
                offset += 4;
                int cmethod = readBytes(data, offset, 2);

                offset += 10;
                int cfsize = readBytes(data, offset, 4);

                offset += 4;
                int ucfsize = readBytes(data, offset, 4);

                offset += 4;
                int fnamelen = readBytes(data, offset, 2);
                offset += 2;
                int fxtrlen = readBytes(data, offset, 2);
                offset += 2;



                offset += fnamelen;
                offset += fxtrlen;
                offset += cfsize;
                LocalFileEntry lfe = new LocalFileEntry(data, lastOffset);


                lfes.add(lfe);

                if (offset < data.length) {
                    processZipFile(arg, data, offset);
                } else {
                    lfes.remove(lfes.size() - 1);
                    repairZipFile(arg, data, lastOffset, lfes);
                }
            }

            if (id == 0x02014B50) {
                if (offset + 30 > data.length) {
                    repairZipFile(arg, data, lastOffset, lfes);
                }
                offset += 0x1c;

                int fnamelen = readBytes(data, offset, 2);
                offset += 2;
                int fxtrlen = readBytes(data, offset, 2);
                offset += 2;
                int fcmtlen = readBytes(data, offset, 2);
                offset += 2;


                offset += fnamelen + fxtrlen + fcmtlen + 12;
                if (offset < data.length)
                    processZipFile(arg, data, offset);
                else {
                 
                }

            }
            if (id == 0x06054B50) {
                if (offset + 20 > data.length) {
                    
                    repairZipFile(arg, data, lastOffset, lfes);
                }
                offset += 20;
                int fcmtlen = readBytes(data, offset, 2);
                if (offset + fcmtlen + 2 == data.length) {
                }
            }
        }
    }

    public byte[] readFileToBuffer(String fname) {
        File f = new File(fname);
        byte data[] = null;
        int len;
        len = (int) f.length();
        data = new byte[len];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            fis.read(data, 0, len);
            fis.close();
        } catch (FileNotFoundException fnfe) {
            logger.error("Exception: ", fnfe);
        }
        catch (IOException ioe) {
            logger.error("Exception: ", ioe);
        }
        finally {

        }
        return data;
    }

    public void repair(String arg) {
        if (arg != null) {
            byte invalid[] = readFileToBuffer(arg);
            processZipFile(arg, invalid, 0);
        }
    }
}
