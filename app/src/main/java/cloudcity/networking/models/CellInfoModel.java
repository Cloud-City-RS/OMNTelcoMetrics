package cloudcity.networking.models;

import com.google.gson.annotations.SerializedName;
import android.telephony.CellInfo;

public class CellInfoModel {

    private Integer earfcn;
    private Integer pci;
    private Long cellId;
    private Integer eNodeBId;
    @SerializedName("dummy_cell_info")
    private Integer dummy;

    public int getEarfcn() {
        return earfcn;
    }

    public void setEarfcn(int earfcn) {
        if (earfcn == CellInfo.UNAVAILABLE) {
            return;
        }
        this.earfcn = earfcn;
    }

    public int getPci() {
        return pci;
    }

    public void setPci(int pci) {
        if (pci == CellInfo.UNAVAILABLE) {
            return;
        }
        this.pci = pci;
    }

    public long getCellId() {
        return cellId;
    }

    public void setCellId(long cellId) {
        this.cellId = cellId;
    }

    public int geteNodeBId() {
        return eNodeBId;
    }

    public void seteNodeBId(int eNodeBId) {
        this.eNodeBId = eNodeBId;
    }

    public Integer getDummy() {
        return dummy;
    }

    public void setDummy(Integer dummy) {
        this.dummy = dummy;
    }
}
