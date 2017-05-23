/*
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2013 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Mupen64PlusAE. If
 * not, see <http://www.gnu.org/licenses/>.
 * 
 * Authors: fzurita
 */
package paulscode.android.mupen64plusae.task;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.mupen64plusae.v3.fzurita.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import paulscode.android.mupen64plusae.ActivityHelper;
import paulscode.android.mupen64plusae.GalleryActivity;
import paulscode.android.mupen64plusae.dialog.ProgressDialog;
import paulscode.android.mupen64plusae.dialog.ProgressDialog.OnCancelListener;
import paulscode.android.mupen64plusae.util.FileUtil;
import paulscode.android.mupen64plusae.util.RomHeader;

public class ExtractRomService extends Service {
    private String mZipPath;
    private String mRomPath;
    private String mExtractZipPath;
    private String mMd5;

    private int mStartId;
    private ServiceHandler mServiceHandler;

    private final IBinder mBinder = new LocalBinder();
    private ExtractRomsListener mListener = null;

    final static int ONGOING_NOTIFICATION_ID = 1;

    public interface ExtractRomsListener {
        //This is called once the ROM scan is finished
        void onExtractRomFinished();

        //This is called when the service is destroyed
        void onExtractRomServiceDestroyed();

        //This is called to get a progress dialog object
        ProgressDialog GetProgressDialog();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public ExtractRomService getService() {
            // Return this instance of ExtractRomService so clients can call public methods
            return ExtractRomService.this;
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            //Check for error conditions
            if (mZipPath == null) {
                if (mListener != null) {
                    mListener.onExtractRomFinished();
                }

                stopSelf(msg.arg1);
                return;
            }

            File zipPathFile = new File(mZipPath);

            if (!zipPathFile.exists()) {
                if (mListener != null) {
                    mListener.onExtractRomFinished();
                }

                stopSelf(msg.arg1);
                return;
            }

            ExtractFileIfNeeded(mMd5, mRomPath, mZipPath);

            if (mListener != null) {
                mListener.onExtractRomFinished();
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    private String ExtractFileIfNeeded(String md5, String romPath, String zipPath) {
        final File romFile = new File(romPath);
        String romFileName = romFile.getName();
        final File extractedRomFile = new File(mExtractZipPath + "/" + romFileName);
        final RomHeader romHeader = new RomHeader(zipPath);

        final boolean isZip = romHeader.isZip;

        if (isZip && (!extractedRomFile.exists())) {
            boolean lbFound = false;

            try {
                final ZipFile zipFile = new ZipFile(zipPath);
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements() && !lbFound) {
                    final ZipEntry zipEntry = entries.nextElement();

                    try {
                        final InputStream zipStream = zipFile.getInputStream(zipEntry);

                        final File destDir = new File(mExtractZipPath);
                        final String entryName = new File(zipEntry.getName()).getName();
                        File tempRomPath = new File(destDir, entryName);
                        final boolean fileExisted = tempRomPath.exists();

                        if (!fileExisted) {
                            tempRomPath = FileUtil.extractRomFile(destDir, zipEntry, zipStream);
                        }

                        final String computedMd5 = ComputeMd5Task.computeMd5(tempRomPath);
                        lbFound = computedMd5 != null && computedMd5.equals(md5);

                        //only delete the file if we extracted our selves
                        if (!lbFound && !fileExisted && tempRomPath != null && !tempRomPath.isDirectory()) {
                            tempRomPath.delete();
                        }

                        zipStream.close();
                    } catch (final IOException e) {
                        Log.w("CacheRomInfoTask", e);
                    }
                }
                zipFile.close();
            } catch (final IOException | ArrayIndexOutOfBoundsException e) {
                Log.w("GalleryActivity", e);
            }
        }

        return extractedRomFile.getPath();
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handle
        Looper serviceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(serviceLooper);

        //Show the notification
        Intent notificationIntent = new Intent(this, GalleryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.extractRomTask_title))
                .setContentText(getString(R.string.toast_pleaseWait))
                .setContentIntent(pendingIntent);
        startForeground(ONGOING_NOTIFICATION_ID, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            mZipPath = extras.getString(ActivityHelper.Keys.ZIP_PATH);
            mExtractZipPath = extras.getString(ActivityHelper.Keys.EXTRACT_ZIP_PATH);
            mRomPath = extras.getString(ActivityHelper.Keys.ROM_PATH);
            mMd5 = extras.getString(ActivityHelper.Keys.ROM_MD5);
        }

        mStartId = startId;

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mListener != null) {
            mListener.onExtractRomServiceDestroyed();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void setExtractRomListener(ExtractRomsListener extractRomListener) {
        mListener = extractRomListener;
        mListener.GetProgressDialog().setOnCancelListener(new OnCancelListener() {
            @Override
            public void OnCancel() {

            }
        });

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = mStartId;
        mServiceHandler.sendMessage(msg);
    }
}
