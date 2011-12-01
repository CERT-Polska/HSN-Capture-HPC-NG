/*
 * (C) NASK, GOVCERT.NL, SURFnet
 * author: jaroslawj NASK 2009-10-27
 */
package pl.nask.hsn.capture.server;

import java.util.Arrays;

public class LocalFileEntry {

    private byte[] ID; 
    private byte[] version; 
    private byte[] gpbf; 
    private byte[] compMethod; 
    private byte[] timestamp; 
    private byte[] crc32; 
    private byte[] cfsize; 
    private byte[] ucfsize;
    private byte[] fnamelen;
    private byte[] fxtrlen; 
    private byte[] fname;
    private byte[] fxtr;
    private int myoffset;

    LocalFileEntry(byte[] data, int offset) {
        myoffset = offset;

        setID(readBytesAsVector(data, offset, 4));
        offset += 4;
        setVersion(readBytesAsVector(data, offset, 2));
        offset += 2;
        setGpbf(readBytesAsVector(data, offset, 2));
        offset += 2;
        setCompMethod(readBytesAsVector(data, offset, 2));
        offset += 2;
        setTimestamp(readBytesAsVector(data, offset, 4));
        offset += 4;
        setCrc32(readBytesAsVector(data, offset, 4));
        offset += 4;
        setCfsize(readBytesAsVector(data, offset, 4));
        offset += 4;
        setUcfsize(readBytesAsVector(data, offset, 4));
        offset += 4;
        setFnamelen(readBytesAsVector(data, offset, 2));
        offset += 2;
        setFxtrlen(readBytesAsVector(data, offset, 2));
        offset += 2;
        setFname(readBytesAsVector(data, offset, readBytes(fnamelen, 0, 2)));
        setFxtr(readBytesAsVector(data, offset, readBytes(fxtrlen, 0, 2)));


    }
    public static byte[] readBytesAsVector(byte data[], int offset, int count) {
        byte[] val = Arrays.copyOfRange(data, offset, offset + count);
        offset += count;
        return val;
    }

    public static int readBytes(byte data[], int offset, int count) {
        int sum = 0;
        int shift = 0;
        for (int i = offset; i < offset + count; ++i) {
            sum += (data[i] & 0xff) << shift;
            shift += 8;
        }
        return sum;
    }

    public byte[] getID() {
        return ID;
    }

    public void setID(byte[] ID) {
        this.ID = ID;
    }

    public byte[] getVersion() {
        return version;
    }

    public void setVersion(byte[] version) {
        this.version = version;
    }

    public byte[] getGpbf() {
        return gpbf;
    }

    public void setGpbf(byte[] gpbf) {
        this.gpbf = gpbf;
    }

    public byte[] getCompMethod() {
        return compMethod;
    }

    public void setCompMethod(byte[] compMethod) {
        this.compMethod = Arrays.copyOfRange(compMethod, 0, compMethod.length);
    }

    public byte[] getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(byte[] timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getCrc32() {
        return crc32;
    }

    public void setCrc32(byte[] crc32) {
        this.crc32 = crc32;
    }

    public byte[] getCfsize() {
        return cfsize;
    }

    public void setCfsize(byte[] cfsize) {
        this.cfsize = cfsize;
    }

    public byte[] getUcfsize() {
        return ucfsize;
    }

    public void setUcfsize(byte[] ucfsize) {
        this.ucfsize = ucfsize;
    }

    public byte[] getFnamelen() {
        return fnamelen;
    }

    public void setFnamelen(byte[] fnamelen) {
        this.fnamelen = fnamelen;
    }

    public byte[] getFxtrlen() {
        return fxtrlen;
    }

    public void setFxtrlen(byte[] fxtrlen) {
        this.fxtrlen = fxtrlen;
    }

    public byte[] getFname() {
        return fname;
    }

    public void setFname(byte[] fname) {
        this.fname = fname;
    }

    public byte[] getFxtr() {
        return fxtr;
    }

    public void setFxtr(byte[] fxtr) {
        this.fxtr = fxtr;
    }
    
    public int getOffset() {
        return myoffset;
    }

    public void setOffset(int offset) {
        this.myoffset = offset;
    }
}
