/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../base/android/java/src/org/chromium/base/process_launcher/ICallbackInt.aidl
 */
package org.chromium.base.process_launcher;
public interface ICallbackInt extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.base.process_launcher.ICallbackInt
{
private static final java.lang.String DESCRIPTOR = "org.chromium.base.process_launcher.ICallbackInt";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.base.process_launcher.ICallbackInt interface,
 * generating a proxy if needed.
 */
public static org.chromium.base.process_launcher.ICallbackInt asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.base.process_launcher.ICallbackInt))) {
return ((org.chromium.base.process_launcher.ICallbackInt)iin);
}
return new org.chromium.base.process_launcher.ICallbackInt.Stub.Proxy(obj);
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
case TRANSACTION_call:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.call(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.base.process_launcher.ICallbackInt
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
@Override public void call(int value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(value);
mRemote.transact(Stub.TRANSACTION_call, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_call = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
public void call(int value) throws android.os.RemoteException;
}
