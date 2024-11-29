package cloudcity.networking.models;

import android.telephony.CellInfo;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * Class combining {@link CellInfoModel} and {@link MeasurementsModel} into one
 */
public class ExtendedCellInfoModel {

    private Integer earfcn;
    private Integer pci;
    private Long cellId;
    private Integer eNodeBId;
    @SerializedName("dummy_cell_info")
    private Integer dummyCell;

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
    private Integer dummyMeasurement;

    @SerializedName("cell_type")
    private int cellType;

    public ExtendedCellInfoModel(
            @NonNull MeasurementsModel measurementsModel,
            @NonNull CellInfoModel cellInfoModel
    ) {
        super();

        // Measurements stuff
        setCsirsrp(measurementsModel.getCsirsrp());
        setCsirsrq(measurementsModel.getCsirsrq());
        setCsisinr(measurementsModel.getCsisinr());
        setSsrsrp(measurementsModel.getSsrsrp());
        setSsrsrq(measurementsModel.getSsrsrq());
        setSssinr(measurementsModel.getSssinr());

        setRsrp(measurementsModel.getRsrp());
        setRsrq(measurementsModel.getRsrq());
        setRssnr(measurementsModel.getRssnr());

        setCellType(measurementsModel.getCellType());
        setDummyMeasurement(measurementsModel.getDummy());

        // Cell info stuff
        setEarfcn(cellInfoModel.getEarfcn());
        setPci(cellInfoModel.getPci());
        setCellId(cellInfoModel.getCellId());
        setDummyCell(cellInfoModel.getDummy());
    }

    public int getEarfcn() {
        return earfcn;
    }

    public void setEarfcn(Integer earfcn) {
        if (earfcn == null || earfcn == CellInfo.UNAVAILABLE) {
            return;
        }
        this.earfcn = earfcn;
    }

    public int getPci() {
        return pci;
    }

    public void setPci(Integer pci) {
        if (pci == null || pci == CellInfo.UNAVAILABLE) {
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

    public Integer getDummyCell() {
        return dummyCell;
    }

    public void setDummyCell(Integer dummy) {
        this.dummyCell = dummy;
    }

    public Integer getRsrp() {
        return rsrp;
    }

    public void setRsrp(Integer rsrp) {
        if (rsrp == null || rsrp == CellInfo.UNAVAILABLE) {
            return;
        }
        this.rsrp = rsrp;
    }

    public Integer getRsrq() {
        return rsrq;
    }

    public void setRsrq(Integer rsrq) {
        if (rsrq == null || rsrq == CellInfo.UNAVAILABLE) {
            return;
        }
        this.rsrq = rsrq;
    }

    public Integer getRssnr() {
        return rssnr;
    }

    public void setRssnr(Integer rssnr) {
        if (rssnr == null || rssnr == CellInfo.UNAVAILABLE) {
            return;
        }
        this.rssnr = rssnr;
    }

    public Integer getCsirsrp() {
        return csirsrp;
    }

    public void setCsirsrp(Integer csirsrp) {
        if (csirsrp == null || csirsrp == CellInfo.UNAVAILABLE) {
            return;
        }
        this.csirsrp = csirsrp;
    }

    public Integer getCsirsrq() {
        return csirsrq;
    }

    public void setCsirsrq(Integer csirsrq) {
        if (csirsrq == null || csirsrq == CellInfo.UNAVAILABLE) {
            return;
        }
        this.csirsrq = csirsrq;
    }

    public Integer getCsisinr() {
        return csisinr;
    }

    public void setCsisinr(Integer csisinr) {
        if (csisinr == null || csisinr == CellInfo.UNAVAILABLE) {
            return;
        }
        this.csisinr = csisinr;
    }

    public Integer getSsrsrp() {
        return ssrsrp;
    }

    public void setSsrsrp(Integer ssrsrp) {
        if (ssrsrp == null || ssrsrp == CellInfo.UNAVAILABLE) {
            return;
        }
        this.ssrsrp = ssrsrp;
    }

    public Integer getSsrsrq() {
        return ssrsrq;
    }

    public void setSsrsrq(Integer ssrsrq) {
        if (ssrsrq == null || ssrsrq == CellInfo.UNAVAILABLE) {
            return;
        }
        this.ssrsrq = ssrsrq;
    }

    public Integer getSssinr() {
        return sssinr;
    }

    public void setSssinr(Integer sssinr) {
        if (sssinr == null || sssinr == CellInfo.UNAVAILABLE) {
            return;
        }
        this.sssinr = sssinr;
    }

    public Integer getDummyMeasurement() {
        return dummyMeasurement;
    }

    public void setDummyMeasurement(Integer dummy) {
        this.dummyMeasurement = dummy;
    }

    public void setCellType(int newType) { this.cellType = newType; }

    public int getCellType() { return cellType; }
}
