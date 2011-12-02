/*
 * (C) NASK, GOVCERT.NL, SURFnet
 * author: jaroslawj NASK 2009-10-27
 */
package pl.nask.hsn.capture.server;

public class CentralDirectoryEndEntry {

    private byte[] ID = {'P', 'K', 0x05, 0x06};
    private byte[] idx = {0, 0};
    private byte[] didx = {0, 0};
    private byte[] diskentries = new byte[2];
    private byte[] direntries = new byte[2];
    private byte[] dirsize = new byte[4];
    private byte[] diroffset = new byte[4];
    private byte[] fcmtlen = {8, 0};
    private byte[] cmt = {'R', 'e', 'p', 'a', 'i', 'r', 'e', 'd'};

    public CentralDirectoryEndEntry(int diskentries, int direntries, int dirsize, int diroffset) {
        this.diskentries[0] = (byte) (diskentries & 0xff);
        this.diskentries[1] = (byte) ((diskentries & 0xff00) >> 8);

        this.direntries = this.diskentries;

        this.dirsize[0] = (byte) (dirsize & 0xff);
        this.dirsize[1] = (byte) ((dirsize & 0xff00) >> 8);
        this.dirsize[2] = (byte) ((dirsize & 0xff0000) >> 16);
        this.dirsize[3] = (byte) ((dirsize & 0xff000000) >> 24);


        this.diroffset[0] = (byte) (diroffset & 0xff);
        this.diroffset[1] = (byte) ((diroffset & 0xff00) >> 8);
        this.diroffset[2] = (byte) ((diroffset & 0xff0000) >> 16);
        this.diroffset[3] = (byte) ((diroffset & 0xff000000) >> 24);

    }

    public byte[] getID() {
        return ID;
    }

    public void setID(byte[] ID) {
        this.ID = ID;
    }

    public byte[] getIdx() {
        return idx;
    }

    public void setIdx(byte[] idx) {
        this.idx = idx;
    }

    public byte[] getDidx() {
        return didx;
    }

    public void setDidx(byte[] didx) {
        this.didx = didx;
    }

    public byte[] getDiskentries() {
        return diskentries;
    }

    public void setDiskentries(byte[] diskentries) {
        this.diskentries = diskentries;
    }

    public byte[] getDirentries() {
        return direntries;
    }

    public void setDirentries(byte[] direntries) {
        this.direntries = direntries;
    }

    public byte[] getDirsize() {
        return dirsize;
    }

    public void setDirsize(byte[] dirsize) {
        this.dirsize = dirsize;
    }

    public byte[] getDiroffset() {
        return diroffset;
    }

    public void setDiroffset(byte[] diroffset) {
        this.diroffset = diroffset;
    }

    public byte[] getFcmtlen() {
        return fcmtlen;
    }

    public void setFcmtlen(byte[] fcmtlen) {
        this.fcmtlen = fcmtlen;
    }

    public byte[] getCmt() {
        return cmt;
    }
}
