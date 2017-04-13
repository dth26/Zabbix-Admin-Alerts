package com.zabbix.danielhui.zab;


import com.zabbix.danielhui.zab.EmailService.MyBinder;
import javax.mail.internet.MimeMessage;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Properties;
import java.util.Timer;

import javax.mail.MessagingException;
import javax.mail.Session;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;




public class MainActivity extends Activity implements EasyPermissions.PermissionCallbacks {
        GoogleAccountCredential mCredential;

        ViewGroup.LayoutParams tlp;
        private TextView mOutputText;
        private Button mCallApiButton;
        ProgressDialog mProgress;
        LinearLayout activityLayout;

        static final int REQUEST_ACCOUNT_PICKER = 1000;
        static final int REQUEST_AUTHORIZATION = 1001;
        static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
        static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

        private static final String BUTTON_TEXT = "Call Gmail API";
        private static final String PREF_ACCOUNT_NAME = "accountName";
        private static final String[] SCOPES = { GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_READONLY, GmailScopes.MAIL_GOOGLE_COM };


        final Handler timerTaskHandler = null;
        final Runnable timerTaskRunnable = null;

        com.google.api.services.gmail.Gmail mService;
    /**
         * Create the main activity.
         * @param savedInstanceState previously saved instance data.
         */
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityLayout = new LinearLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            activityLayout.setLayoutParams(lp);
            activityLayout.setOrientation(LinearLayout.VERTICAL);
            activityLayout.setPadding(16, 16, 16, 16);

            tlp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            mCallApiButton = new Button(this);
            mCallApiButton.setText(BUTTON_TEXT);
            mCallApiButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCallApiButton.setEnabled(false);
                   // mOutputText.setText("klajsdklfjaskdlf");
                    try {
                        //getResultsFromApi();
                        getResultsFromApi();
                    } catch (MessagingException e) { Log.d("onCreate() : ", e.toString()); }
                    mCallApiButton.setEnabled(true);
                }
            });
            activityLayout.addView(mCallApiButton);

            mOutputText = new TextView(this);
            mOutputText.setLayoutParams(tlp);
            mOutputText.setPadding(16, 16, 16, 16);
            mOutputText.setVerticalScrollBarEnabled(true);
            mOutputText.setMovementMethod(new ScrollingMovementMethod());

           // setAlertText("Click the \'" + BUTTON_TEXT +"\' button to test the API.");
            mOutputText.setText(
                   "Click the \'" + BUTTON_TEXT +"\' button to test the API.");
            activityLayout.addView(mOutputText);

            mProgress = new ProgressDialog(this);
            mProgress.setMessage("Calling Gmail API ...");

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
            timerTaskHandler.removeCallbacks(timerTaskRunnable);
        }



        /**
         * Attempt to call the API, after verifying that all the preconditions are
         * satisfied. The preconditions are: Google Play Services installed, an
         * account was selected and the device currently has online access. If any
         * of the preconditions are not satisfied, the app will prompt the user as
         * appropriate.
         */
        private void getResultsFromApi() throws  MessagingException {
            if (! isGooglePlayServicesAvailable()) {
                acquireGooglePlayServices();
            } else if (mCredential.getSelectedAccountName() == null) {
                chooseAccount();
            } else if (! isDeviceOnline()) {
                mOutputText.setText("No network connection available.");
            } else {
                mOutputText.setText("Syncing...");
                new MakeRequestTask(this).execute();

            }
        }


        private void setRepeatingAsyncTaskGetEmails() {

            final Handler handler = new Handler();
            Timer timer = new Timer();


            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        public void run() {
                            try {
                                getResultsFromApi();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            };

            timer.schedule(task, 0, 600000);  // interval of 10 minute

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
            if (EasyPermissions.hasPermissions(
                    this, Manifest.permission.GET_ACCOUNTS)) {
                String accountName = getPreferences(Context.MODE_PRIVATE)
                        .getString(PREF_ACCOUNT_NAME, null);
                if (accountName != null) {
                    mCredential.setSelectedAccountName(accountName);
                    try{
                        //getResultsFromApi();
                        setRepeatingAsyncTaskGetEmails();
                    } catch(Exception e) { Log.d("chooseAccount() : ", e.toString()) ; }
                } else {
                    // Start a dialog from which the user can choose an account
                    startActivityForResult(
                            mCredential.newChooseAccountIntent(),
                            REQUEST_ACCOUNT_PICKER);
                }
            } else {
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

            try {
                switch (requestCode) {
                    case REQUEST_GOOGLE_PLAY_SERVICES:
                        if (resultCode != RESULT_OK) {
                            mOutputText.setText(
                                    "This app requires Google Play Services. Please install " +
                                            "Google Play Services on your device and relaunch this app.");
                        } else {
                            //getResultsFromApi();
                            setRepeatingAsyncTaskGetEmails();
                        }
                        break;
                    case REQUEST_ACCOUNT_PICKER:
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
                                //getResultsFromApi();
                                setRepeatingAsyncTaskGetEmails();
                            }
                        }
                        break;
                    case REQUEST_AUTHORIZATION:
                        if (resultCode == RESULT_OK) {
                            setRepeatingAsyncTaskGetEmails();
                          //  getResultsFromApi();
                        }
                        break;
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

        /**
         * Checks whether the device currently has a network connection.
         * @return true if the device has a network connection, false otherwise.
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

            mOutputText.setText(text);

        }

        /*
        *   Google Api Calls must be called in a AsyncTask to ensure responsive ui
        *
        *
        * */
        private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
            private Exception mLastError = null;
            MainActivity mainRef;

            public MakeRequestTask(MainActivity ref) {
                mainRef = ref;
            }

            @Override
            protected List<String> doInBackground(Void... params) {
                List<String> emails = null;
                try {
                        emailService.initiateService(mService);
                        emails = emailService.getDataFromApi();



                } catch (Exception e) {
                        mLastError = e;
                        cancel(true);
                        return null;
                }

                return emails;
            }

//            @Override
//            protected void onProgressUpdate(List<String> emails) throws  MessagingException, IOException{
//                List<String> emails = emailService.getDataFromApi();
//                System.out.println("yoyo");
//                mainRef.setAlertText(emails.get(0));
//            }

            @Override
            protected void onPostExecute(List<String> emails){
                System.out.println("Executing onPostExecute");


                mainRef.setAlertText(emails.get(0));
            }
        }

        /**
         * An asynchronous task that handles the Gmail API call.
         * Placing the API calls in their own task ensures the UI stays responsive.
         */
       /* private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
            private com.google.api.services.gmail.Gmail mService = null;
            private Exception mLastError = null;

            MakeRequestTask(GoogleAccountCredential credential) {
                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                mService = new com.google.api.services.gmail.Gmail.Builder(
                        transport, jsonFactory, credential)
                        .setApplicationName("Gmail API Android Quickstart")
                        .build();
            }

            /**
             * Background task to call Gmail API.
             * @param params no parameters needed for this task.
             */
       /*
            @Override
            protected List<String> doInBackground(Void... params) {
//                try {
//                    return getDataFromApi();
//                } catch (Exception e) {
//                    mLastError = e;
//                    cancel(true);
//                    return null;
//                }

                return null;
            }

*/

            /**
             * Fetch a list of Gmail labels attached to the specified account.
             * @return List of Strings labels.
             * @throws IOException
             */
            /*
            private List<String> getDataFromApi() throws IOException, MessagingException{
                // Get the labels in the user's account.
                String user = "me";
                List<String> emailMessages = new ArrayList<String>();
                ListLabelsResponse listResponse =
                        mService.users().labels().list(user).execute();

                // Set email as read
                List<String> labelsToRemove = new ArrayList<String>();
                labelsToRemove.add("UNREAD");


                //ListMessagesResponse messageResponse =  mService.users().messages().list(user).setQ("is:unread").execute();
                ListMessagesResponse messageResponse =  mService.users().messages().list(user).setQ("label:UNREAD").execute();

                for(Message message : messageResponse.getMessages()) {

                    ModifyMessageRequest mods = new ModifyMessageRequest().setRemoveLabelIds(labelsToRemove);
                    Message messageText = mService.users().messages().get(user, message.getId()).setFormat("raw").execute();

                    Long timeStamp =messageText.getInternalDate();
                    java.util.Date time=new java.util.Date((long)timeStamp*1000);

                    Base64 base64Url = new Base64(true);
                    byte[] emailBytes = base64Url.decodeBase64(messageText.getRaw());


                    Properties props = new Properties();
                    Session session = Session.getDefaultInstance(props, null);

                    MimeMessage email = new MimeMessage(session, new ByteArrayInputStream(emailBytes));

                    System.out.println( time);
                    System.out.println(email.getSubject());
                 //   System.out.println(email.);


                    // set email as read
                    Message messageReturn = mService.users().messages().modify(user, message.getId(), mods).execute();

                    emailMessages.add(time + ": \n" + email.getSubject());
                }

//                for (Label label : listResponse.getLabels()) {
//                    labels.add(label.getName());
//                }

                return emailMessages;
            }


            @Override
            protected void onPreExecute() {
                mOutputText.setText("");
                mProgress.show();
            }

            @Override
            protected void onPostExecute(List<String> output) {
                mProgress.hide();
                if (output == null || output.size() == 0) {
                    mOutputText.setText("No results returned.");
                } else {
                    output.add(0, "Data retrieved using the Gmail API:");
                    mOutputText.setText(TextUtils.join("\n", output));
                }
            }

            @Override
            protected void onCancelled() {
                mProgress.hide();
                if (mLastError != null) {
                    if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                        showGooglePlayServicesAvailabilityErrorDialog(
                                ((GooglePlayServicesAvailabilityIOException) mLastError)
                                        .getConnectionStatusCode());
                    } else if (mLastError instanceof UserRecoverableAuthIOException) {
                        startActivityForResult(
                                ((UserRecoverableAuthIOException) mLastError).getIntent(),
                                MainActivity.REQUEST_AUTHORIZATION);
                    } else {
                        mOutputText.setText("The following error occurred:\n"
                                + mLastError.getMessage());
                    }
                } else {
                    mOutputText.setText("Request cancelled.");
                }
            }
        }
        */
}
