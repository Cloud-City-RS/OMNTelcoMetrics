package de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class NetworkDataModelRequest {

    @SerializedName("sensor_events")
    private List<NetworkDataModel> data;

    public NetworkDataModelRequest(){
        data = new ArrayList<>();
    }

    public NetworkDataModelRequest(List<NetworkDataModel> data) {
        this.data = data;
    }

    public List<NetworkDataModel> getData() {
        return data;
    }

    public void setData(List<NetworkDataModel> data) {
        this.data = data;
    }

    public void add(NetworkDataModel dataModel) {
        data.add(dataModel);
    }

    public void clear() {
        data.clear();
    }
}
