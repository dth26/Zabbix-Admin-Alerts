package com.zabbix.danielhui.zab;


import com.zabbix.danielhui.zab.EmailService.MyBinder;
import javax.mail.internet.MimeMessage;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.ToggleButton;
import android.widget.Switch;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.gmail.GmailScopes;

import com.google.api.services.gmail.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
//import android.util.Base64;
import android.util.Log;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Date;
import java.text.SimpleDateFormat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Properties;
import java.util.Timer;

import javax.mail.MessagingException;
import javax.mail.Session;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


import android.content.BroadcastReceiver;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.widget.Toast;

public class MainActivity extends Activity implements EasyPermissions.PermissionCallbacks {
        GoogleAccountCredential mCredential;

        ViewGroup.LayoutParams tlp;
        private TextView alertOutputText;
        private TextView emailText;
        private TextView syncOutputText;
        private Button emailToggleButton;
        private Button alarmToggleButton;
        private Button clearButton;
        private Button syncButton;
        private RelativeLayout activityLayout;
        private LinearLayout alertsLayout;
        CheckBox chkVAMF;
        CheckBox chkMAE;

        Handler handler = null;
        Timer timer = null;

        static final int REQUEST_ACCOUNT_PICKER = 1000;
        static final int REQUEST_AUTHORIZATION = 1001;
        static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
        static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

        private static final String PREF_ACCOUNT_NAME = "accountName";
        private static final String[] SCOPES = { GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_READONLY, GmailScopes.MAIL_GOOGLE_COM };



        Ringtone ringtone;

        com.google.api.services.gmail.Gmail mService;



    /**
         * Create the main activity.
         * @param savedInstanceState previously saved instance data.
         */
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_main);

            activityLayout =  (RelativeLayout) findViewById(R.id.activityLayout);
            alertsLayout = (LinearLayout) findViewById(R.id.alertsView);
            alarmToggleButton = (ToggleButton) findViewById(R.id.alarmToggle);
            emailToggleButton = (Button) findViewById(R.id.emailToggle);
            emailText = (TextView) findViewById(R.id.emailText);
            clearButton = (Button) findViewById(R.id.clearButton);
            clearButton.setTextColor(Color.WHITE);

            syncButton = (Button) findViewById(R.id.forceSyncButton);
            syncOutputText = (TextView) findViewById(R.id.syncOutputText);
            chkVAMF = (CheckBox) findViewById(R.id.chkVAMF);
            chkMAE = (CheckBox) findViewById(R.id.chkMAE);


            clearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertsLayout.removeAllViews();

                    if(ringtone != null)
                        ringtone.stop();
                }
            });


            syncButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        getResultsFromApi();
                    } catch (Exception e) { e.printStackTrace(); }

                }
            });

            emailToggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chooseAccount();
                }
            });

            alarmToggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    String nextAlarmState = (String) alarmToggleButton.getText();

                    if(nextAlarmState.equals("ON")) {
                        syncOutputText.setText("");
                    } else {
                        syncOutputText.setText("Alarm is turned off. Turn it on!");
                    }
                }
            });


            chkMAE.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(chkMAE.isChecked() || chkVAMF.isChecked())
                        syncOutputText.setText("No Sync Yet");
                    else
                        syncOutputText.setText("Neither VAMF or MAE is checked above!");

                }
            });


            chkVAMF.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(chkMAE.isChecked() || chkVAMF.isChecked())
                        syncOutputText.setText("No Sync Yet");
                    else
                        syncOutputText.setText("Neither VAMF or MAE is checked above!");

                }
            });



            setContentView(activityLayout);

            // Initialize credentials and service object.
            mCredential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff());


            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, mCredential)
                    .setApplicationName("Gmail API")
                    .build();



            // Start gmail syncing process in background
            System.out.println("mCredential Account Name");
            System.out.println(mCredential.getSelectedAccountName());
            if (mCredential.getSelectedAccountName() != null) {
                setRepeatingAsyncTaskGetEmails();
            } else {
                System.out.println("choosing account()");
                chooseAccount();
            }
        }


        EmailService emailService;


        @Override
        protected void onStart() {
            super.onStart();
            // start email polling service
            Intent intent = new Intent(this, EmailService.class );
            startService(intent);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            removeAllAlerts();
           // timerTaskHandler.removeCallbacks(timerTaskRunnable);
            timer.cancel();
            timer = null;
        }



        /**
         * Attempt to call the API, after verifying that all the preconditions are
         * satisfied. The preconditions are: Google Play Services installed, an
         * account was selected and the device currently has online access. If any
         * of the preconditions are not satisfied, the app will prompt the user as
         * appropriate.
         */
        private void getResultsFromApi() throws  MessagingException {

            String toggleState = (String)alarmToggleButton.getText();


            if (!isGooglePlayServicesAvailable()) {
                acquireGooglePlayServices();
            } else if (mCredential.getSelectedAccountName() == null) {
                syncOutputText.setText("Please choose email account above!");
                //chooseAccount();
            } else if (!isDeviceOnline()) {
                syncOutputText.setText("No network connection available.");
            } else if(toggleState.equals("OFF")) {
                syncOutputText.setText("Alarm is turned off. Turn it on!");
            } else if (!chkVAMF.isChecked() && !chkMAE.isChecked()) {
                syncOutputText.setText("Neither VAMF or MAE is checked above!");
            } else {
                syncOutputText.setText("Syncing with Email Client...");
                new MakeRequestTask(this, chkVAMF.isChecked(), chkMAE.isChecked()).execute();

            }
        }




        private void setRepeatingAsyncTaskGetEmails() {

            handler = new Handler();
            timer = new Timer();


            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        public void run() {
                            try {
                                removeAllAlerts();
                                getResultsFromApi();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            };

            //timer.schedule(task, 0, 600000);  // interval of 10 minute
            timer.schedule(task, 0, 60000);  // interval of 10 minute

        }



        private ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {

                MyBinder myBinder = (MyBinder) service;
                emailService = myBinder.getService();
                Log.v("ServiceConnection","connected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.v("ServiceConnection","disconnected");
            }
        };
        /**
         * Attempts to set the account used with the API credentials. If an account
         * name was previously saved it will use that one; otherwise an account
         * picker dialog will be shown to the user. Note that the setting the
         * account to use with the credentials object requires the app to have the
         * GET_ACCOUNTS permission, which is requested here if it is not already
         * present. The AfterPermissionGranted annotation indicates that this
         * function will be rerun automatically whenever the GET_ACCOUNTS permission
         * is granted.
         */
        @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
        private void chooseAccount() {

            System.out.println("In fact choosing an account");
            if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {

                String accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null);

//                if (accountName != null ) {
//                    emailText.setText(accountName);
//                    mCredential.setSelectedAccountName(accountName);
//                }
                //else {
                   //  Start a dialog from which the user can choose an account

                    startActivityForResult(
                            mCredential.newChooseAccountIntent(),
                            REQUEST_ACCOUNT_PICKER);
             //   }

            } else {

                emailText.setText("");
                // Request the GET_ACCOUNTS permission via a user dialog
                EasyPermissions.requestPermissions(
                        this,
                        "This app needs to access your Google account (via Contacts).",
                        REQUEST_PERMISSION_GET_ACCOUNTS,
                        Manifest.permission.GET_ACCOUNTS);
            }
        }

        /**
         * Called when an activity launched here (specifically, AccountPicker
         * and authorization) exits, giving you the requestCode you started it with,
         * the resultCode it returned, and any additional data from it.
         * @param requestCode code indicating which activity result is incoming.
         * @param resultCode code indicating the result of the incoming
         *     activity result.
         * @param data Intent (containing result data) returned by incoming
         *     activity result.
         */
        @Override
        protected void onActivityResult( int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            System.out.println("==============================================");
            System.out.println("==============================================");
            System.out.println("==============================================");
            System.out.println("===========OnActivityResult()=================");
            System.out.println("==============================================");
            System.out.println("==============================================");
            System.out.println("==============================================");


            try {
                switch (requestCode) {
                    case REQUEST_GOOGLE_PLAY_SERVICES:
                        if (resultCode != RESULT_OK) {
                            System.out.println("REQUEST_GOOGLE_PLAY_SERVICES 1");
                            alertOutputText.setText(
                                    "This app requires Google Play Services. Please install " +
                                            "Google Play Services on your device and relaunch this app.");
                        } else {
                            System.out.println("REQUEST_GOOGLE_PLAY_SERVICES 2 ");
                           // setRepeatingAsyncTaskGetEmails();
                        }
                        break;
                    case REQUEST_ACCOUNT_PICKER:
                        System.out.println("REQUEST_ACCOUNT_PICKER");
                        if (resultCode == RESULT_OK && data != null &&
                                data.getExtras() != null) {
                            String accountName =
                                    data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                            if (accountName != null) {
                                SharedPreferences settings =
                                        getPreferences(Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putString(PREF_ACCOUNT_NAME, accountName);
                                editor.apply();
                                mCredential.setSelectedAccountName(accountName);
                                emailText.setText(accountName);
                                //getResultsFromApi();
                            //    setRepeatingAsyncTaskGetEmails();
                            }
                        }
                        break;
                    case REQUEST_AUTHORIZATION:
                        System.out.println("REQUEST_AUTHORIZATION");
                        if (resultCode == RESULT_OK) {
                         //   setRepeatingAsyncTaskGetEmails();
                          //  getResultsFromApi();
                        }
                        break;
                }

                if(timer==null) {
                    setRepeatingAsyncTaskGetEmails();
                }

            } catch (Exception e) {
                Log.d("onActivityResult() : ", e.toString());
            }
        }

        /**
         * Respond to requests for permissions at runtime for API 23 and above.
         * @param requestCode The request code passed in
         *     requestPermissions(android.app.Activity, String, int, String[])
         * @param permissions The requested permissions. Never null.
         * @param grantResults The grant results for the corresponding permissions
         *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
         */
        @Override
        public void onRequestPermissionsResult(int requestCode,
                                               @NonNull String[] permissions,
                                               @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            EasyPermissions.onRequestPermissionsResult(
                    requestCode, permissions, grantResults, this);
        }

        /**
         * Callback for when a permission is granted using the EasyPermissions
         * library.
         * @param requestCode The request code associated with the requested
         *         permission
         * @param list The requested permission list. Never null.
         */
        @Override
        public void onPermissionsGranted(int requestCode, List<String> list) {
            // Do nothing.
        }

        /**
         * Callback for when a permission is denied using the EasyPermissions
         * library.
         * @param requestCode The request code associated with the requested
         *         permission
         * @param list The requested permission list. Never null.
         */
        @Override
        public void onPermissionsDenied(int requestCode, List<String> list) {
            // Do nothing.
        }

        /**network connection.
         * @return true if the device has a network co
         * Checks whether the device currently has a nnection, false otherwise.
         */
        private boolean isDeviceOnline() {
            ConnectivityManager connMgr =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            return (networkInfo != null && networkInfo.isConnected());
        }

        /**
         * Check that Google Play services APK is installed and up to date.
         * @return true if Google Play Services is available and up to
         *     date on this device; false otherwise.
         */
        private boolean isGooglePlayServicesAvailable() {
            GoogleApiAvailability apiAvailability =
                    GoogleApiAvailability.getInstance();
            final int connectionStatusCode =
                    apiAvailability.isGooglePlayServicesAvailable(this);
            return connectionStatusCode == ConnectionResult.SUCCESS;
        }

        /**
         * Attempt to resolve a missing, out-of-date, invalid or disabled Google
         * Play Services installation via a user dialog, if possible.
         */
        private void acquireGooglePlayServices() {
            GoogleApiAvailability apiAvailability =
                    GoogleApiAvailability.getInstance();
            final int connectionStatusCode =
                    apiAvailability.isGooglePlayServicesAvailable(this);
            if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
                showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            }
        }


        /**
         * Display an error dialog showing that Google Play Services is missing
         * or out of date.
         * @param connectionStatusCode code describing the presence (or lack of)
         *     Google Play Services on this device.
         */
        void showGooglePlayServicesAvailabilityErrorDialog(
                final int connectionStatusCode) {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            Dialog dialog = apiAvailability.getErrorDialog(
                    MainActivity.this,
                    connectionStatusCode,
                    REQUEST_GOOGLE_PLAY_SERVICES);
            dialog.show();
        }

        public void setAlertText(String text) {

            TextView alertText = new TextView(getApplicationContext());
            alertText.setText(text);
            alertText.setTextColor(Color.parseColor("#000000"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0,15,0,15);
            alertText.setLayoutParams(lp);
            alertsLayout.addView(alertText);
        }

        public void removeAllAlerts() {
            if(alertsLayout.getChildCount() > 0)
                alertsLayout.removeAllViews();
        }

        public void setSyncText(boolean successfulSync) {

            SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yy h:mm:ss a");
            String formattedDate = dateFormat.format(new Date()).toString();

            if (successfulSync)
                syncOutputText.setText("Last Successful Sync Occurred " + formattedDate);
            else {
            //    syncOutputText.setTextColor(Color.parseColor("#e02f2f"));
                syncOutputText.setText("Sync Failed at " + formattedDate);
            }


        }

        /*
        *   Google Api Calls must be called in a AsyncTask to ensure responsive ui
        *
        *
        * */
        private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
            private Exception mLastError = null;
            private boolean alarmEnabled;
            private boolean vamfChecked;
            private boolean maeChecked;
            MainActivity mainRef;

            public MakeRequestTask(MainActivity ref, boolean vamfChecked, boolean maeChecked) {
                mainRef = ref;
                this.vamfChecked = vamfChecked;
                this.maeChecked = maeChecked;
            }

            @Override
            protected void onPreExecute() {
                alarmEnabled = mainRef.alarmToggleButton.isEnabled();

            }

            @Override
            protected List<String> doInBackground(Void... params) {
                List<String> emails = null;
                try {

                    emailService.initiateService(mService);
                    emails = emailService.getDataFromApi(maeChecked, vamfChecked);
                    System.out.println(emails);

                } catch (Exception e) {
                        mLastError = e;
                }

                return emails;
            }



            @Override
            protected void onPostExecute(List<String> emails){
                System.out.println("Executing onPostExecute");

                removeAllAlerts();

                if(emails == null) {
                    mainRef.setSyncText(false);
                } else if (emails.size()==0) {
                    mainRef.setSyncText(true);
                    mainRef.setAlertText("No Zabbix Alerts!");
                }else {
                    mainRef.setSyncText(true);

                    for(int i=0; i<emails.size(); i++) {
                        mainRef.setAlertText(emails.get(i));
                    }

                    //this will sound the alarm tone
                    //this will sound the alarm once, if you wish to
                    if ( alarmEnabled ) {
                        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
//                        if (alarmUri == null) {
//                            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//                        }
                        ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
                        ringtone.play();
                    }
                }

            }
        }
}

