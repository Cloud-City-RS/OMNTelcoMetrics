package de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.models;

import android.telephony.CellInfo;

import com.google.gson.annotations.SerializedName;

public class MeasurementsModel {

    // LTE
    private Integer rsrp;
    private Integer rsrq;
    private Integer rssnr;

    // 5G
    private Integer csirsrp;
    private Integer csirsrq;
    private Integer csisinr;
    private Integer ssrsrp;
    private Integer ssrsrq;
    private Integer sssinr;

    @SerializedName("dummy_value")
    private Integer dummy;

    public Integer getRsrp() {
        return rsrp;
    }

    public void setRsrp(Integer rsrp) {
        if (rsrp == CellInfo.UNAVAILABLE) {
            return;
        }
        this.rsrp = rsrp;
    }

    public Integer getRsrq() {
        return rsrq;
    }

    public void setRsrq(Integer rsrq) {
        if (rsrq == CellInfo.UNAVAILABLE) {
            return;
        }
        this.rsrq = rsrq;
    }

    public Integer getRssnr() {
        return rssnr;
    }

    public void setRssnr(Integer rssnr) {
        if (rssnr == CellInfo.UNAVAILABLE) {
            return;
        }
        this.rssnr = rssnr;
    }

    public Integer getCsirsrp() {
        return csirsrp;
    }

    public void setCsirsrp(Integer csirsrp) {
        if (csirsrp == CellInfo.UNAVAILABLE) {
            return;
        }
        this.csirsrp = csirsrp;
    }

    public Integer getCsirsrq() {
        return csirsrq;
    }

    public void setCsirsrq(Integer csirsrq) {
        if (csirsrq == CellInfo.UNAVAILABLE) {
            return;
        }
        this.csirsrq = csirsrq;
    }

    public Integer getCsisinr() {
        return csisinr;
    }

    public void setCsisinr(Integer csisinr) {
        if (csisinr == CellInfo.UNAVAILABLE) {
            return;
        }
        this.csisinr = csisinr;
    }

    public Integer getSsrsrp() {
        return ssrsrp;
    }

    public void setSsrsrp(Integer ssrsrp) {
        if (ssrsrp == CellInfo.UNAVAILABLE) {
            return;
        }
        this.ssrsrp = ssrsrp;
    }

    public Integer getSsrsrq() {
        return ssrsrq;
    }

    public void setSsrsrq(Integer ssrsrq) {
        if (ssrsrq == CellInfo.UNAVAILABLE) {
            return;
        }
        this.ssrsrq = ssrsrq;
    }

    public Integer getSssinr() {
        return sssinr;
    }

    public void setSssinr(Integer sssinr) {
        if (sssinr == CellInfo.UNAVAILABLE) {
            return;
        }
        this.sssinr = sssinr;
    }

    public Integer getDummy() {
        return dummy;
    }

    public void setDummy(Integer dummy) {
        this.dummy = dummy;
    }

    @Override
    public String toString() {
        return "MeasurementsModel{" +
                "rsrp=" + rsrp +
                ", rsrq=" + rsrq +
                ", rssnr=" + rssnr +
                ", csirsrp=" + csirsrp +
                ", csirsrq=" + csirsrq +
                ", csisinr=" + csisinr +
                ", ssrsrp=" + ssrsrp +
                ", ssrsrq=" + ssrsrq +
                ", sssinr=" + sssinr +
                ", dummy=" + dummy +
                '}';
    }
}
