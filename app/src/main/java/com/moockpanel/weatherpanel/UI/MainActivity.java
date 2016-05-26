package com.moockpanel.weatherpanel.UI;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mixpanel.android.mpmetrics.Tweak;
import com.moockpanel.weatherpanel.R;
import com.moockpanel.weatherpanel.weather.Current;
import com.moockpanel.weatherpanel.weather.Day;
import com.moockpanel.weatherpanel.weather.Forecast;
import com.moockpanel.weatherpanel.weather.Hour;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String DAILY_FORECAST = "DAILY_FORECAST";
    public static final String HOURLY_FORECAST = "HOURLY_FORECAST";
    public static final String LOCALE = "LOCALE";

    String projectToken = "365cdb37e7391ca2df93c4c672d84190";
    MixpanelAPI mMixpanel;
    MixpanelAPI.People mPeople;
    private static Tweak<String> tempFormat = MixpanelAPI.stringTweak("Temperature Format", "F");
    private static Tweak<Boolean> showAds = MixpanelAPI.booleanTweak("Show ads", false);

    private AdView mAdView;
    private AdRequest mAdRequest;

    private Forecast mForecast;
    private Location mLastLocation;
    private double mLatitude;
    private double mLongitude;
    private String mLocale;

    GoogleApiClient mGoogleApiClient;

    @InjectView(R.id.locationLabel)
    TextView mLocationLabel;
    @InjectView(R.id.timeLabel)
    TextView mTimeLabel;
    @InjectView(R.id.temperatureLabel)
    TextView mTemperatureLabel;
    @InjectView(R.id.humidityValue)
    TextView mHumidityValue;
    @InjectView(R.id.precipValue)
    TextView mPrecipValue;
    @InjectView(R.id.summaryLabel)
    TextView mSummaryLabel;
    @InjectView(R.id.iconImageView)
    ImageView mIconImageView;
    @InjectView(R.id.refreshImageView)
    ImageView mRefreshImageView;
    @InjectView(R.id.progressBar)
    ProgressBar mProgressBar;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient mClient;

    FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        buildGoogleApiClient();
        mGoogleApiClient.connect();
//        Toast.makeText(this, mGoogleApiClient.isConnected() + "", Toast.LENGTH_SHORT).show();

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mMixpanel = MixpanelAPI.getInstance(this, projectToken);
        mPeople = mMixpanel.getPeople();
        String distinctId = mMixpanel.getDistinctId();
        mMixpanel.identify(distinctId);
        mPeople.identify(distinctId);
        mPeople.initPushHandling("85502243623");

        // firebase bundle
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.LEVEL, "1");
        bundle.putString(FirebaseAnalytics.Param.SEARCH_TERM, "HEY");
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "App Open");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.ECOMMERCE_PURCHASE, bundle);

        mAdView = (AdView) findViewById(R.id.adView);
        mAdRequest = new AdRequest.Builder().build();

        mProgressBar.setVisibility(View.INVISIBLE);

        try {
            JSONObject props = new JSONObject();
            props.put("Fresh Launch", true);
            mMixpanel.track("App Open", props);
            mPeople.increment("App Opens", 1);
        } catch (JSONException e) {
            Log.e("MYAPP", "Unable to add properties to JSONObject", e);
        }


        mRefreshImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getForecast(mLatitude, mLongitude);
            }
        });

        Log.d(TAG, "Main UI code is running");
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mClient = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    protected synchronized void buildGoogleApiClient() {
        // get some google apis up in this bitch
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void getForecast(double latitude, double longitude) {
        String apiKey = "019dcf77d8799e887cb616e6898adc47";
        String forecastUrl = "https://api.forecast.io/forecast/" + apiKey + "/"
                + latitude + "," + longitude;

//        Toast.makeText(this, mGoogleApiClient.isConnected() + "", Toast.LENGTH_SHORT).show();
        if (isNetworkAvailable()) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(forecastUrl)
                    .build();

            Call call = client.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });
                    alertUserAboutError();
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });
                    try {
                        String jsonData = response.body().string();
                        boolean requestStatus;
                        Log.v(TAG, jsonData);
                        if (response.isSuccessful()) {
                            mForecast = parseForecastDetails(jsonData);
                            requestStatus = true;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateDisplay();
                                    if (showAds.get() != false) {
                                        findViewById(R.id.hourlybutton).setVisibility(View.INVISIBLE);
                                        findViewById(R.id.dailyButton).setVisibility(View.INVISIBLE);
                                        mAdView.loadAd(mAdRequest);
                                    }
                                }
                            });
                        } else {
                            alertUserAboutError();
                            requestStatus = false;
                        }
                        JSONObject props = new JSONObject();
                        props.put("Request Status", requestStatus);
                        mMixpanel.track("Weather Refreshed", props);
                        mPeople.increment("Weather Refreshes", 1);
                    } catch (IOException e) {
                        Log.e(TAG, "Exception caught", e);
                    } catch (JSONException e) {
                        Log.e(TAG, "Exception caught", e);
                    }
                }
            });
        } else {
            Toast.makeText(this, R.string.network_unavailable_message,
                    Toast.LENGTH_LONG).show();
        }


    }

    private void updateDisplay() {
        Current current = mForecast.getCurrent();

        mLocationLabel.setText(mLocale);
        int fcTemp;
        if (tempFormat.get().equals("C")) {
            fcTemp = ((current.getTemperature() - 32) * 5 / 9);
        } else {
            fcTemp = current.getTemperature();
        }
        mTemperatureLabel.setText(fcTemp + "");
        mTimeLabel.setText("At " + current.getFormattedTime() + " it will be");
        mHumidityValue.setText(current.getHumidity() + "");
        mPrecipValue.setText(current.getPrecipChance() + "%");
        mSummaryLabel.setText(current.getSummary());
        Drawable drawable = getResources().getDrawable(current.getIconId());
        mIconImageView.setImageDrawable(drawable);

    }

    private Forecast parseForecastDetails(String jsonData) throws JSONException {
        Forecast forecast = new Forecast();

        forecast.setCurrent(getCurrentDetails(jsonData));
        forecast.setHourlyForecast(getHourlyForecast(jsonData));
        forecast.setDailyForecast(getDailyForecst(jsonData));

        return forecast;
    }

    private Day[] getDailyForecst(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject daily = forecast.getJSONObject("daily");
        JSONArray data = daily.getJSONArray("data");

        Day[] days = new Day[data.length()];

        for (int i = 0; i < data.length(); i++) {
            JSONObject jsonDay = data.getJSONObject(i);
            Day day = new Day();

            day.setSummary(jsonDay.getString("summary"));
            day.setIcon(jsonDay.getString("icon"));
            day.setTemperatureMax(jsonDay.getDouble("temperatureMax"));
            day.setTime(jsonDay.getLong("time"));
            day.setTimezone(timezone);

            days[i] = day;
        }
        return days;
    }

    private Hour[] getHourlyForecast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONArray data = hourly.getJSONArray("data");

        Hour[] hours = new Hour[data.length()];

        for (int i = 0; i < data.length(); i++) {
            JSONObject jsonHour = data.getJSONObject(i);
            Hour hour = new Hour();

            hour.setSummary(jsonHour.getString("summary"));
            hour.setTemperature(jsonHour.getDouble("temperature"));
            hour.setIcon(jsonHour.getString("icon"));
            hour.setTime(jsonHour.getLong("time"));
            hour.setTimezone(timezone);

            hours[i] = hour;
        }

        return hours;
    }

    private Current getCurrentDetails(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        Log.i(TAG, "From JSON: " + timezone);

        JSONObject currently = forecast.getJSONObject("currently");

        Current current = new Current();
        current.setHumidity(currently.getDouble("humidity"));
        current.setTime(currently.getLong("time"));
        current.setIcon(currently.getString("icon"));
        current.setPrecipChance(currently.getDouble("precipProbability"));
        current.setSummary(currently.getString("summary"));
        current.setTemperature(currently.getDouble("temperature"));
        current.setTimeZone(timezone);

        Log.d(TAG, current.getFormattedTime());
        Log.d(TAG, current.getTemperature() + "");

        return current;
    }

    private boolean isNetworkAvailable() {
        toggleRefresh();

        ConnectivityManager manager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            isAvailable = true;
        }
        return isAvailable;
    }

    private void toggleRefresh() {
        if (mProgressBar.getVisibility() == View.INVISIBLE) {
            mProgressBar.setVisibility(View.VISIBLE);
            mRefreshImageView.setVisibility(View.INVISIBLE);
        } else {
            mProgressBar.setVisibility(View.INVISIBLE);
            mRefreshImageView.setVisibility(View.VISIBLE);
        }
    }

    private void alertUserAboutError() {
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    private void reverseGeocode(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;
        try {
            addresses = geocoder.getFromLocation(latitude, longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (addresses.size() > 0) mLocale = addresses.get(0).getLocality();

    }

    @OnClick(R.id.dailyButton)
    public void startDailyActivity(View view) {
        Intent intent = new Intent(this, DailyForecastActivity.class);
        intent.putExtra(DAILY_FORECAST, mForecast.getDailyForecast());
        intent.putExtra(LOCALE, mLocale);
        startActivity(intent);
    }

    @OnClick(R.id.hourlybutton)
    public void startHourlyActivity(View view) {
        Intent intent = new Intent(this, HourlyForecastActivity.class);
        intent.putExtra(HOURLY_FORECAST, mForecast.getHourlyForecast());
        startActivity(intent);
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            mLatitude = mLastLocation.getLatitude();
            mLongitude = mLastLocation.getLongitude();
            reverseGeocode(mLatitude, mLongitude);
            getForecast(mLatitude, mLongitude);
        }
        Toast.makeText(this, mLocale + "", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mClient.disconnect();
    }
}
