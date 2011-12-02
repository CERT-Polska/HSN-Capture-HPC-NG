/*
 * (C) NASK, GOVCERT.NL, SURFnet
 * author: jaroslawj NASK 2009-10-27
 */

package pl.nask.hsn.capture.server;

public class CentralDirectoryEntry {
    private byte[] ID = {'P', 'K', 0x01, 0x02};
    private byte[] version = {0x14};
    private byte[] hostos = {0};
    private byte[] minversion = {0x14};
    private byte[] targetos = {0};
    private byte[] gpbf;
    private byte[] compMethod;
    private byte[] timestamp;
    private byte[] crc32;
    private byte[] cfsize;
    private byte[] ucfsize;

    private byte[] fnamelen;
    private byte[] fxtrlen;
    private static byte[] fcmtlen = {0, 0};
    private static byte[] disknumber = {0, 0};
    private static byte[] attrs = {0, 0};
    private static byte[] extattrs = {0, 0, 0, 0};
    private byte[] reloffset = new byte[4];
    private byte[] fname;
    private byte[] fxtr;
    private byte[] fcmt;

    public static int readBytes(byte data[], int offset, int count) {
        int sum = 0;
        int shift = 0;
        for (int i = offset; i < offset + count; ++i) {
            sum += (data[i] & 0xff) << shift;
            shift += 8;
        }
        return sum;
    }

    public CentralDirectoryEntry(LocalFileEntry lfe) {
        this.gpbf = lfe.getGpbf();
        this.compMethod = lfe.getCompMethod();
        this.timestamp = lfe.getTimestamp();
        this.crc32 = lfe.getCrc32();
        this.cfsize = lfe.getCfsize();
        this.ucfsize = lfe.getUcfsize();
        this.fnamelen = lfe.getFnamelen();
        this.fxtrlen = lfe.getFxtrlen();
        this.fname = lfe.getFname();
        this.fxtr = lfe.getFxtr();

        this.reloffset[0] = (byte) ((lfe.getOffset() & 0xff));
        this.reloffset[1] = (byte) ((lfe.getOffset() & 0xff00) >> 8);
        this.reloffset[2] = (byte) ((lfe.getOffset() & 0xff0000) >> 16);
        this.reloffset[3] = (byte) ((lfe.getOffset() & 0xff000000) >> 24);


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

    public byte[] getHostos() {
        return hostos;
    }

    public void setHostos(byte[] hostos) {
        this.hostos = hostos;
    }

    public byte[] getMinversion() {
        return minversion;
    }

    public void setMinversion(byte[] minversion) {
        this.minversion = minversion;
    }

    public byte[] getTargetos() {
        return targetos;
    }

    public void setTargetos(byte[] targetos) {
        this.targetos = targetos;
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
        this.compMethod = compMethod;
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

    public byte[] getFcmtlen() {
        return fcmtlen;
    }

    public static byte[] getDisknumber() {
        return disknumber;
    }

    public static void setDisknumber(byte[] disknumber) {
        CentralDirectoryEntry.disknumber = disknumber;
    }

    public static byte[] getAttrs() {
        return attrs;
    }

    public static void setAttrs(byte[] attrs) {
        CentralDirectoryEntry.attrs = attrs;
    }

    public static byte[] getExtattrs() {
        return extattrs;
    }

    public static void setExtattrs(byte[] extattrs) {
        CentralDirectoryEntry.extattrs = extattrs;
    }

    public byte[] getReloffset() {
        return reloffset;
    }

    public void setReloffset(byte[] reloffset) {
        this.reloffset = reloffset;
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

    public byte[] getFcmt() {
        return fcmt;
    }

    public void setFcmt(byte[] fcmt) {
        this.fcmt = fcmt;
    }
}
