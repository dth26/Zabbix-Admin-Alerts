package com.zabbix.danielhui.zab;

import android.app.Service;
import android.os.IBinder;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.Binder;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

public class EmailService extends Service{

    private IBinder mBinder = new MyBinder();
    GoogleAccountCredential mCredential;
    private com.google.api.services.gmail.Gmail mService;
    private static final String[] SCOPES = { GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_READONLY, GmailScopes.MAIL_GOOGLE_COM };
    private static String TAG = "Email Service";
    private Context context;
    private boolean isServiceRunning = false;

    public void initiateService(com.google.api.services.gmail.Gmail mService){
//        mCredential = GoogleAccountCredential.usingOAuth2(
//                getApplicationContext(), Arrays.asList(SCOPES))
//                .setBackOff(new ExponentialBackOff());
//
//
//        HttpTransport transport = AndroidHttp.newCompatibleTransport();
//        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
//        System.out.println("Initiated Service..." );
//        System.out.println(mCredential);
//        mService = new com.google.api.services.gmail.Gmail.Builder(
//                transport, jsonFactory, mCredential)
//                .setApplicationName("Gmail API")
//                .build();
        this.mService = mService;
        isServiceRunning = true;
    }

    public boolean isServiceRunning(){
        return isServiceRunning;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        Log.d(TAG, "EmailService binded");
        return mBinder;
    }



    @Override
    public void onStart(Intent intent, int startId) {
        // TODO Auto-generated method stub
        super.onStart(intent, startId);
        Log.d(TAG, "EmailService started");
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        Log.d(TAG, "EmailService destroyed");
        isServiceRunning = false;
        super.onDestroy();
    }


    public List<String> getDataFromApi() throws IOException, MessagingException {
        // Get the labels in the user's account.
        String user = "me";
        List<String> emailMessages = new ArrayList<String>();



        // Set email as read
        List<String> labelsToRemove = new ArrayList<String>();
        labelsToRemove.add("UNREAD");

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

              //  System.out.println( time);
              //  System.out.println(email.getSubject());

                // set email as read
                Message messageReturn = mService.users().messages().modify(user, message.getId(), mods).execute();

                emailMessages.add(time + ": \n" + email.getSubject());
        }


        return emailMessages;
    }



    public class MyBinder extends Binder{

        EmailService getService() {
            return EmailService.this;
        }
    }
}
