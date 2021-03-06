package edu.ucla.cens.stresschillmap;

import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import org.xmlpull.v1.XmlPullParser;

import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Overlay;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.GeoPoint;

import android.os.Bundle;
import android.os.Vibrator;
import android.os.Looper;

import android.util.Log;
import android.util.AttributeSet;
import android.util.Xml;

import android.widget.LinearLayout;
import android.widget.ZoomControls;
import android.widget.Toast;

import android.graphics.drawable.Drawable;

import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.content.res.Resources;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;

import android.app.Activity;

public class map extends MapActivity {
    private Context ctx;
    private SharedPreferences preferences;
    private LinearLayout linear_layout;
    private MapView map_view;
    private ZoomControls zoom_controls;
    List<Overlay> overlay_list;
    Drawable marker;
    MySiteOverlay site_overlay;
    MyLocationOverlay location_overlay;
    private String TAG = "Map View";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.map);

        ctx = map.this;
        preferences = this.getSharedPreferences(getString(R.string.preferences), Activity.MODE_PRIVATE);

        linear_layout = (LinearLayout) findViewById (R.id.zoomview);
        map_view = (MapView) findViewById (R.id.mapview);
        overlay_list = map_view.getOverlays ();
        zoom_controls = (ZoomControls) map_view.getZoomControls();
        marker = this.getResources().getDrawable (R.drawable.androidmarker);

        // add zoom controls to the map view
        linear_layout.addView (zoom_controls);

        // set zoom level to 16 (pretty good default zoom level)
        map_view.getController().setZoom(16);

        // focus the map on the current GPS location
        location_overlay = new MyLocationOverlay (this, map_view);
        location_overlay.enableMyLocation();
        location_overlay.runOnFirstFix(new Runnable() {
                public void run () {
                    map_view.getController().animateTo(location_overlay.getMyLocation());
                }
            }
        );
        overlay_list.add (location_overlay);


        // create an overlay and populate it with sites from the
        // appengine database
        site_overlay = new MySiteOverlay (marker);
        overlay_list.add (site_overlay);
    }

    protected void onPause() {
        if (null != location_overlay) {
            location_overlay.disableMyLocation();
        }
        super.onPause();
    }

    protected void onResume() {
        super.onResume();
        if (null != location_overlay) {
            location_overlay.enableMyLocation();
        }
    }

    protected void onStop() {
        if (null != location_overlay) {
            location_overlay.disableMyLocation();
        }
        map.this.finish();
        super.onStop();
    }

    protected void onStart() {
        super.onStart();
        if (null != location_overlay) {
            location_overlay.enableMyLocation();
        }
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu (Menu m) {
        super.onCreateOptionsMenu (m);

        m.add (Menu.NONE, 0, Menu.NONE, "Home").setIcon (android.R.drawable.ic_menu_revert);
        m.add (Menu.NONE, 1, Menu.NONE, "Survey").setIcon (android.R.drawable.ic_menu_agenda);
        m.add (Menu.NONE, 2, Menu.NONE, "About").setIcon (android.R.drawable.ic_menu_info_details);
        m.add (Menu.NONE, 3, Menu.NONE, "Instructions").setIcon (android.R.drawable.ic_menu_help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem index) {
        Intent i;
        switch (index.getItemId()) {
            case 0:
                i = new Intent (ctx, home.class);
                break;
            case 1:
                i = new Intent (ctx, survey.class);
                break;
            case 2:
                i = new Intent (ctx, about.class);
                break;
            case 3:
                i = new Intent (ctx, instructions.class);
                break;
            default:
                return false;
        }
        ctx.startActivity (i);
        return true;
    }

    public class MySiteOverlay<Item> extends ItemizedOverlay {
        private ArrayList<OverlayItem> overlay_items = new ArrayList<OverlayItem>();
        private int last_tap_index = -1;
        private float down_x = -1;
        private float down_y = -1;
        private long down_t = -1;
        private Thread vibrator;
        private final int long_press_delay = 1000; // milliseconds
        private final int max_dx = 15;
        private final int max_dy = 15;
    
        MySiteOverlay(Drawable defaultMarker) {
            super (boundCenterBottom(defaultMarker));

            String point_url = getString(R.string.map_point_summary);
            String point_data = getUrlData (point_url);

            if (null == point_data) {
                populate();
                return;
            }

            int i = 0;

            Log.d(TAG, "spot alpha");

            try {
                JSONObject json = new JSONObject (point_data.toString());
                if (null == json) {
                    populate();
                    return;
                }

                for (i = 0;; i++) {
                    if (!json.has(Integer.toString(i))) {
                        break;
                    }

                    JSONObject entry = json.getJSONObject(Integer.toString(i));

                    if (!entry.has("stressval")) {
                        continue;
                    }
                    String text = "Stress Level: " +
                                  Double.toString(entry.getDouble("stressval"));

                    if (!entry.has("latitude")) {
                        continue;
                    }
                    Double lat = Double.valueOf(entry.getString("latitude"));

                    if (!entry.has("longitude")) {
                        continue;
                    }
                    Double lon = Double.valueOf(entry.getString("longitude"));

                    if (!entry.has("key")) {
                        continue;
                    }
                    String key = entry.getString("key");

                    overlay_items.add (new OverlayItem (get_point(lat, lon), text, key));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (java.lang.NullPointerException e) {
                e.printStackTrace();
            } catch (java.lang.OutOfMemoryError e) { 
                e.printStackTrace();
            }

            populate();
        }

        private GeoPoint get_point (double lat, double lon) {
            return new GeoPoint ((int)(lat*1000000.0), (int)(lon*1000000.0));
        }

        private String getUrlData(String url) {
            String websiteData = null;
            try {
                DefaultHttpClient client = new DefaultHttpClient();
                URI uri = new URI(url);
                HttpGet method = new HttpGet(uri);
                HttpResponse res = client.execute(method);
                InputStream data = res.getEntity().getContent();
                websiteData = generateString(data);
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                return "";
            } catch (IOException e) {
                e.printStackTrace();
                return "";
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return "";
            }
            return websiteData;
        }

        private String generateString(InputStream stream) {
            InputStreamReader reader = new InputStreamReader(stream);
            BufferedReader buffer = new BufferedReader(reader);
            StringBuilder sb = new StringBuilder();

            try {
                String cur;
                while ((cur = buffer.readLine()) != null) {
                    sb.append(cur + "\n");
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            try {
                stream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return sb.toString();
        }


    
        public void addOverlay (OverlayItem overlay) {
            overlay_items.add (overlay);
            populate();
        }
    
        @Override
        protected OverlayItem createItem (int i) {
            return overlay_items.get(i);
        }
    
        @Override
        public int size () {
            return overlay_items.size();
        }

        @Override
        protected boolean onTap (int index) {
            last_tap_index = index;
            ((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(50);
            preferences.edit().putString("site_key", overlay_items.get(last_tap_index).getSnippet()).commit();
            map.this.startActivity (new Intent(map.this, popup.class));
            return true;
        }
    }

}
