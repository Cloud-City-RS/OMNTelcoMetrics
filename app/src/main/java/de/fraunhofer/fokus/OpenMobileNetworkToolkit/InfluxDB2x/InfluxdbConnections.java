/*
 *  SPDX-FileCopyrightText: 2023 Peter Hasse <peter.hasse@fokus.fraunhofer.de>
 *  SPDX-FileCopyrightText: 2023 Johann Hackler <johann.hackler@fokus.fraunhofer.de>
 *  SPDX-FileCopyrightText: 2023 Fraunhofer FOKUS
 *
 *  SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package de.fraunhofer.fokus.OpenMobileNetworkToolkit.InfluxDB2x;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import cloudcity.util.CloudCityLogger;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class InfluxdbConnections {
    private static final String TAG = "InfluxdbConnections";
    private static InfluxdbConnection ric;
    private static InfluxdbConnection lic;

    private InfluxdbConnections() {
    }


    public static InfluxdbConnection getRicInstance(Context context) {
        if (ric == null) {
            SharedPreferencesGrouper spg = SharedPreferencesGrouper.getInstance(context);
            String url = spg.getSharedPreference(SPType.logging_sp).getString("influx_URL", "");
            String org = spg.getSharedPreference(SPType.logging_sp).getString("influx_org", "");
            String bucket = spg.getSharedPreference(SPType.logging_sp).getString("influx_bucket", "");
            String token = spg.getSharedPreference(SPType.logging_sp).getString("influx_token", "");
            if (url.isEmpty() || org.isEmpty() || bucket.isEmpty() || token.isEmpty()) {
                CloudCityLogger.e(TAG, "Influx parameters incomplete, can't setup logging");
                // if we are an UI thread we make a toast, if not logging have to be enough
                if (Looper.getMainLooper().isCurrentThread()) {
                    Toast.makeText(context, "Influx Parameter not correctly set!", Toast.LENGTH_LONG).show();// On UI thread.
                }
                return null;
            }
            ric = new InfluxdbConnection(url, token, org, bucket, context);
        }
        return ric;
    }

    public static void removeRicInstance() {
        ric = null;
    }

    //todo Remote setting are currently hardcoded and should be generated
    public static InfluxdbConnection getLicInstance(Context context) {
        if (lic == null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String url = "http://127.0.0.1:8086";
            String org = "omnt";
            String bucket = "omnt";
            String token = "1234567890";
            if (url.isEmpty() || org.isEmpty() || bucket.isEmpty() || token.isEmpty()) {
                CloudCityLogger.e(TAG, "Influx parameters incomplete, can't setup logging");
                return null;
            }
            lic = new InfluxdbConnection(url, token, org, bucket, context);
        }
        return lic;
    }
}
