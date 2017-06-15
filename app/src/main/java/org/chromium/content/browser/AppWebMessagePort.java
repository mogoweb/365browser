// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.browser.MessagePort;

import java.util.Arrays;

/**
 * Represents the MessageChannel MessagePort object. Inspired from
 * http://www.whatwg.org/specs/web-apps/current-work/multipage/web-messaging.html#message-channels
 *
 * State management:
 *
 * A message port can be in transferred state while a transfer is pending or complete. An
 * application cannot use a transferred port to post messages. If a transferred port
 * receives messages, they will be queued. This state is not visible to embedder app.
 *
 * A message port should be closed by the app when it is not needed any more. This will free
 * any resources used by it. A closed port cannot receive/send messages and cannot be transferred.
 * close() can be called multiple times. A transferred port cannot be closed by the application,
 * since the ownership is also transferred during the transfer. Closing a transferred port will
 * throw an exception.
 *
 * The fact that messages can be handled on a separate thread means that thread
 * synchronization is important. All methods are called on UI thread except as noted.
 *
 * Restrictions:
 * The HTML5 message protocol is very flexible in transferring ports. However, this
 * sometimes leads to surprising behavior. For example, in current version of chrome (m41)
 * the code below
 *  1.  var c1 = new MessageChannel();
 *  2.  var c2 = new MessageChannel();
 *  3.  c1.port2.onmessage = function(e) { console.log("1"); }
 *  4.  c2.port2.onmessage = function(e) {
 *  5.     e.ports[0].onmessage = function(f) {
 *  6.          console.log("3");
 *  7.      }
 *  8.  }
 *  9.  c1.port1.postMessage("test");
 *  10. c2.port1.postMessage("test2",[c1.port2])
 *
 * prints 1 or 3 depending on whether or not line 10 is included in code. Further if
 * it gets executed with a timeout, depending on timeout value, the printout value
 * changes.
 *
 * To prevent such problems, this implementation limits the transfer of ports
 * as below:
 * A port is put to a "started" state if:
 * 1. The port is ever used to post a message, or
 * 2. The port was ever registered a handler to receive a message.
 * A started port cannot be transferred.
 *
 * This restriction should not impact postmessage functionality in a big way,
 * because an app can still create as many channels as it wants to and use it for
 * transferring data. As a return, it simplifies implementation and prevents hard
 * to debug, racy corner cases while receiving/sending data.
 */
@JNINamespace("content")
public class AppWebMessagePort implements MessagePort {
    private static final String TAG = "AppWebMessagePort";
    private static final long UNINITIALIZED_PORT_NATIVE_PTR = 0;

    // The |what| value for handleMessage.
    private static final int MESSAGES_AVAILABLE = 1;

    // Implements the handler to handle messageport messages received from web.
    // These messages are received on IO thread and normally handled in main
    // thread however, alternatively application can pass a handler to execute them.
    private static class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGES_AVAILABLE) {
                AppWebMessagePort port = (AppWebMessagePort) msg.obj;
                port.dispatchReceivedMessages();
                return;
            }
            throw new IllegalStateException("undefined message");
        }
    }
    // The default message handler
    private static final MessageHandler sDefaultHandler =
            new MessageHandler(Looper.getMainLooper());

    private long mNativeAppWebMessagePort = UNINITIALIZED_PORT_NATIVE_PTR;
    private MessageCallback mMessageCallback;
    private boolean mClosed;
    private boolean mTransferred;
    private boolean mStarted;
    private MessageHandler mHandler;
    private final Object mLock = new Object();

    // Called to create an entangled pair of ports.
    public static AppWebMessagePort[] createPair() {
        AppWebMessagePort[] ports =
            new AppWebMessagePort[] { new AppWebMessagePort(), new AppWebMessagePort() };
        nativeInitializeAppWebMessagePortPair(ports);
        return ports;
    }

    @Override
    public boolean isReady() {
        return mNativeAppWebMessagePort != UNINITIALIZED_PORT_NATIVE_PTR;
    }

    @CalledByNative
    private void setNativeAppWebMessagePort(long nativeAppWebMessagePort) {
        mNativeAppWebMessagePort = nativeAppWebMessagePort;
    }

    @CalledByNative
    private long releaseNativePortForTransfer() {
        mTransferred = true;
        long port = mNativeAppWebMessagePort;
        mNativeAppWebMessagePort = UNINITIALIZED_PORT_NATIVE_PTR;
        return port;
    }

    @Override
    public void close() {
        if (mTransferred) {
            throw new IllegalStateException("Port is already transferred");
        }
        if (mClosed) return;
        mClosed = true;
        // Synchronize with dispatchReceivedMessages to ensure that the native
        // port is not closed too soon, but avoid holding mLock while calling
        // nativeCloseMessagePort as that could result in a dead-lock (racing
        // with onMessagesAvailable).
        long port = UNINITIALIZED_PORT_NATIVE_PTR;
        synchronized (mLock) {
            port = mNativeAppWebMessagePort;
            mNativeAppWebMessagePort = UNINITIALIZED_PORT_NATIVE_PTR;
        }
        if (port != UNINITIALIZED_PORT_NATIVE_PTR) {
            nativeCloseMessagePort(port);
        }
    }

    @Override
    public boolean isClosed() {
        return mClosed;
    }

    @Override
    public boolean isTransferred() {
        return mTransferred;
    }

    @Override
    public boolean isStarted() {
        return mStarted;
    }

    // Only called on UI thread
    @Override
    public void setMessageCallback(MessageCallback messageCallback, Handler handler) {
        if (isClosed() || isTransferred()) {
            throw new IllegalStateException("Port is already closed or transferred");
        }
        mStarted = true;
        synchronized (mLock) {
            mMessageCallback = messageCallback;
            if (handler != null) {
                mHandler = new MessageHandler(handler.getLooper());
            }
        }
        nativeStartReceivingMessages(mNativeAppWebMessagePort);
    }

    // Called on a background thread.
    @CalledByNative
    private void onMessagesAvailable() {
        synchronized (mLock) {
            Handler handler = mHandler != null ? mHandler : sDefaultHandler;
            Message msg = handler.obtainMessage(MESSAGES_AVAILABLE, this);
            handler.sendMessage(msg);
        }
    }

    // This method is called by nativeDispatchNextMessage while mLock is held.
    @CalledByNative
    private void onReceivedMessage(String message, AppWebMessagePort[] ports) {
        if (mMessageCallback == null) {
            Log.w(TAG, "No handler set for port [" + mNativeAppWebMessagePort
                    + "], dropping message " + message);
            return;
        }
        mMessageCallback.onMessage(message, ports);
    }

    // This method may be called on either the UI thread or a background thread.
    private void dispatchReceivedMessages() {
        // Dispatch all of the available messages unless interrupted by close().
        // NOTE: nativeDispatchNextMessage returns true and calls onReceivedMessage
        // if a message is available else it returns false.
        while (true) {
            synchronized (mLock) {
                if (!(isReady() && nativeDispatchNextMessage(mNativeAppWebMessagePort))) {
                    break;
                }
            }
        }
    }

    @Override
    public void postMessage(String message, MessagePort[] sentPorts) throws IllegalStateException {
        if (isClosed() || isTransferred()) {
            throw new IllegalStateException("Port is already closed or transferred");
        }
        AppWebMessagePort[] ports = null;
        if (sentPorts != null) {
            for (MessagePort port : sentPorts) {
                if (port.equals(this)) {
                    throw new IllegalStateException("Source port cannot be transferred");
                }
                if (port.isClosed() || port.isTransferred()) {
                    throw new IllegalStateException("Port is already closed or transferred");
                }
                if (port.isStarted()) {
                    throw new IllegalStateException("Port is already started");
                }
            }
            ports = Arrays.copyOf(sentPorts, sentPorts.length, AppWebMessagePort[].class);
        }
        mStarted = true;
        nativePostMessage(mNativeAppWebMessagePort, message, ports);
    }

    private static native void nativeInitializeAppWebMessagePortPair(AppWebMessagePort[] ports);

    private native void nativeCloseMessagePort(long nativeAppWebMessagePort);
    private native void nativePostMessage(long nativeAppWebMessagePort, String message,
                                          AppWebMessagePort[] ports);
    private native boolean nativeDispatchNextMessage(long nativeAppWebMessagePort);
    private native void nativeStartReceivingMessages(long nativeAppWebMessagePort);
}
