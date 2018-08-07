package com.example.revelationorange.transflo_take2;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

public class InfoWindowClasses {
    public static class CustomInfoWindow implements GoogleMap.InfoWindowAdapter {
        private Context context;

        public CustomInfoWindow(Context ctx) { context = ctx; }

        public View getInfoWindow(Marker m) {
            return null;
        }

        public View getInfoContents(Marker m) {
            View v = ((Activity) context).getLayoutInflater()
                    .inflate(R.layout.custom_info_window, null);
            TextView nameBox = v.findViewById(R.id.nameBox);
            TextView distBox = v.findViewById(R.id.distanceBox);
            TextView addrBox = v.findViewById(R.id.addressBox);
            TextView rl2Box = v.findViewById(R.id.rawLine2);
            TextView rl3Box = v.findViewById(R.id.rawLine3);

            InfoWindowData iwd = (InfoWindowData) m.getTag();

            nameBox.setText(iwd.getName());
            distBox.setText(iwd.getDistance());
            addrBox.setText(iwd.getAddress());
            if (iwd.getRawLine2() != null) {
                rl2Box.setText(iwd.getRawLine2());
            } else {
                rl2Box.setVisibility(TextView.GONE);
            }
            if (iwd.getRawLine3() != null) {
                rl3Box.setText(iwd.getRawLine3());
            } else {
                rl3Box.setVisibility(TextView.GONE);
            }

            return v;
        }
    }

    public static class InfoWindowData {
        private String name;
        private String distance;
        private String address;
        private String rawLine2;
        private String rawLine3;

        public void setAddress(String address) {
            this.address = address;
        }

        public void setDistance(String distance) {
            this.distance = distance;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setRawLine2(String rawLine2) {
            this.rawLine2 = rawLine2;
        }

        public void setRawLine3(String rawLine3) {
            this.rawLine3 = rawLine3;
        }

        public String getAddress() {
            return address;
        }

        public String getDistance() {
            return distance;
        }

        public String getName() {
            return name;
        }

        public String getRawLine2() {
            return rawLine2;
        }

        public String getRawLine3() {
            return rawLine3;
        }
    }
}
