// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.photo_picker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.StrictMode;

import org.chromium.base.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;

/**
 * A class to communicate with the {@link DecoderService}.
 */
public class DecoderServiceHost {
    // A tag for logging error messages.
    private static final String TAG = "ImageDecoderHost";

    /**
     * Interface for notifying clients of the service being ready.
     */
    public interface ServiceReadyCallback {
        /**
         * A function to define to receive a notification once the service is up and running.
         */
        void serviceReady();
    }

    /**
     * An interface notifying clients when an image has finished decoding.
     */
    public interface ImageDecodedCallback {
        /**
         * A function to define to receive a notification that an image has been decoded.
         * @param filePath The file path for the newly decoded image.
         * @param bitmap The results of the decoding (or placeholder image, if failed).
         */
        void imageDecodedCallback(String filePath, Bitmap bitmap);
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private class DecoderServiceConnection implements ServiceConnection {
        // The callback to use to notify the service being ready.
        private ServiceReadyCallback mCallback;

        public DecoderServiceConnection(ServiceReadyCallback callback) {
            mCallback = callback;
        }

        // Called when a connection to the service has been established.
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            mBound = true;
            mCallback.serviceReady();
        }

        // Called when a connection to the service has been lost.
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    }

    /**
     * Class for keeping track of the data involved with each request.
     */
    private static class DecoderServiceParams {
        // The path to the file containing the bitmap to decode.
        public String mFilePath;

        // The requested size (width and height) of the bitmap, once decoded.
        public int mSize;

        // The callback to use to communicate the results of the decoding.
        ImageDecodedCallback mCallback;

        public DecoderServiceParams(String filePath, int size, ImageDecodedCallback callback) {
            mFilePath = filePath;
            mSize = size;
            mCallback = callback;
        }
    }

    // Map of file paths to decoder parameters in order of request.
    private LinkedHashMap<String, DecoderServiceParams> mRequests = new LinkedHashMap<>();
    LinkedHashMap<String, DecoderServiceParams> getRequests() {
        return mRequests;
    }

    // The callback used to notify the client when the service is ready.
    private ServiceReadyCallback mCallback;

    // Messenger for communicating with the remote service.
    Messenger mService = null;

    // Our service connection to the {@link DecoderService}.
    private DecoderServiceConnection mConnection;

    // Flag indicating whether we are bound to the service.
    boolean mBound;

    // The inbound messenger used by the remote service to communicate with us.
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    /**
     * The DecoderServiceHost constructor.
     * @param callback The callback to use when communicating back to the client.
     */
    public DecoderServiceHost(ServiceReadyCallback callback) {
        mCallback = callback;
    }

    /**
     * Initiate binding with the {@link DecoderService}.
     * @param context The context to use.
     */
    public void bind(Context context) {
        mConnection = new DecoderServiceConnection(mCallback);
        Intent intent = new Intent(context, DecoderService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbind from the {@link DecoderService}.
     * @param context The context to use.
     */
    public void unbind(Context context) {
        if (mBound) {
            context.unbindService(mConnection);
            mBound = false;
        }
    }

    /**
     * Accepts a request to decode a single image. Queues up the request and reports back
     * asynchronously on |callback|.
     * @param filePath The path to the file to decode.
     * @param size The requested size (width and height) of the resulting bitmap.
     * @param callback The callback to use to communicate the decoding results.
     */
    public void decodeImage(String filePath, int size, ImageDecodedCallback callback) {
        DecoderServiceParams params = new DecoderServiceParams(filePath, size, callback);
        mRequests.put(filePath, params);
        if (mRequests.size() == 1) dispatchNextDecodeImageRequest();
    }

    /**
     * Dispatches the next image for decoding (from the queue).
     */
    private void dispatchNextDecodeImageRequest() {
        if (mRequests.entrySet().iterator().hasNext()) {
            DecoderServiceParams params = mRequests.entrySet().iterator().next().getValue();
            dispatchDecodeImageRequest(params.mFilePath, params.mSize);
        }
    }

    /**
     * Ties up all the loose ends from the decoding request (communicates the results of the
     * decoding process back to the client, and takes care of house-keeping chores regarding
     * the request queue).
     * @param filePath The path to the image that was just decoded.
     * @param bitmap The resulting decoded bitmap.
     */
    public void closeRequest(String filePath, Bitmap bitmap) {
        DecoderServiceParams params = getRequests().get(filePath);
        if (params != null) {
            params.mCallback.imageDecodedCallback(filePath, bitmap);
            getRequests().remove(filePath);
        }
        dispatchNextDecodeImageRequest();
    }

    /**
     * Communicates with the server to decode a single bitmap.
     * @param filePath The path to the image on disk.
     * @param size The requested width and height of the resulting bitmap.
     */
    private void dispatchDecodeImageRequest(String filePath, int size) {
        // Obtain a file descriptor to send over to the sandboxed process.
        File file = new File(filePath);
        FileInputStream inputFile = null;
        ParcelFileDescriptor pfd = null;
        Bundle bundle = new Bundle();

        // The restricted utility process can't open the file to read the
        // contents, so we need to obtain a file descriptor to pass over.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            try {
                inputFile = new FileInputStream(file);
                FileDescriptor fd = inputFile.getFD();
                pfd = ParcelFileDescriptor.dup(fd);
                bundle.putParcelable(DecoderService.KEY_FILE_DESCRIPTOR, pfd);
            } catch (IOException e) {
                Log.e(TAG, "Unable to obtain FileDescriptor: " + e);
                closeRequest(filePath, null);
            }
        } finally {
            try {
                if (inputFile != null) inputFile.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close inputFile: " + e);
            }
            StrictMode.setThreadPolicy(oldPolicy);
        }

        if (pfd == null) return;

        // Prepare and send the data over.
        Message payload = Message.obtain(null, DecoderService.MSG_DECODE_IMAGE);
        payload.replyTo = mMessenger;
        bundle.putString(DecoderService.KEY_FILE_PATH, filePath);
        bundle.putInt(DecoderService.KEY_SIZE, size);
        payload.setData(bundle);
        try {
            mService.send(payload);
            pfd.close();
        } catch (RemoteException e) {
            Log.e(TAG, "Communications failed (Remote): " + e);
            closeRequest(filePath, null);
        } catch (IOException e) {
            Log.e(TAG, "Communications failed (IO): " + e);
            closeRequest(filePath, null);
        }
    }

    /**
     * Cancels a request to decode an image (if it hasn't already been dispatched).
     * @param filePath The path to the image to cancel decoding.
     */
    public void cancelDecodeImage(String filePath) {
        mRequests.remove(filePath);
    }

    /**
     * A class for handling communications from the service to us.
     */
    static class IncomingHandler extends Handler {
        // The DecoderServiceHost object to communicate with.
        private final WeakReference<DecoderServiceHost> mHost;

        /**
         * Constructor for IncomingHandler.
         * @param host The DecoderServiceHost object to communicate with.
         */
        IncomingHandler(DecoderServiceHost host) {
            mHost = new WeakReference<DecoderServiceHost>(host);
        }

        @Override
        public void handleMessage(Message msg) {
            DecoderServiceHost host = mHost.get();
            if (host == null) {
                super.handleMessage(msg);
                return;
            }

            switch (msg.what) {
                case DecoderService.MSG_IMAGE_DECODED_REPLY:
                    Bundle payload = msg.getData();

                    // Read the reply back from the service.
                    String filePath = payload.getString(DecoderService.KEY_FILE_PATH);
                    Boolean success = payload.getBoolean(DecoderService.KEY_SUCCESS);
                    Bitmap bitmap = success
                            ? (Bitmap) payload.getParcelable(DecoderService.KEY_IMAGE_BITMAP)
                            : null;
                    host.closeRequest(filePath, bitmap);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
