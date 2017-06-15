/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../content/public/android/java/src/org/chromium/IsReadyToPayService.aidl
 */
package org.chromium;
/**
 * Interface to determine whether a payment app
 * is ready for payment.
 */
public interface IsReadyToPayService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.IsReadyToPayService
{
private static final java.lang.String DESCRIPTOR = "org.chromium.IsReadyToPayService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.IsReadyToPayService interface,
 * generating a proxy if needed.
 */
public static org.chromium.IsReadyToPayService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.IsReadyToPayService))) {
return ((org.chromium.IsReadyToPayService)iin);
}
return new org.chromium.IsReadyToPayService.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_isReadyToPay:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.IsReadyToPayServiceCallback _arg0;
_arg0 = org.chromium.IsReadyToPayServiceCallback.Stub.asInterface(data.readStrongBinder());
this.isReadyToPay(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.IsReadyToPayService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
/**
     * Method that will be called on the Service to query
     * whether the payment app is ready for payment.
     *
     * @param callback The callback to report back to the browser.
     */
@Override public void isReadyToPay(org.chromium.IsReadyToPayServiceCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_isReadyToPay, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_isReadyToPay = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
/**
     * Method that will be called on the Service to query
     * whether the payment app is ready for payment.
     *
     * @param callback The callback to report back to the browser.
     */
public void isReadyToPay(org.chromium.IsReadyToPayServiceCallback callback) throws android.os.RemoteException;
}
