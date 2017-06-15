/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/custom_tabs_client/src/customtabs/src/android/support/customtabs/IPostMessageService.aidl
 */
package android.support.customtabs;
/**
 * Interface to a PostMessageService.
 * @hide
 */
public interface IPostMessageService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements android.support.customtabs.IPostMessageService
{
private static final java.lang.String DESCRIPTOR = "android.support.customtabs.IPostMessageService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an android.support.customtabs.IPostMessageService interface,
 * generating a proxy if needed.
 */
public static android.support.customtabs.IPostMessageService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof android.support.customtabs.IPostMessageService))) {
return ((android.support.customtabs.IPostMessageService)iin);
}
return new android.support.customtabs.IPostMessageService.Stub.Proxy(obj);
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
case TRANSACTION_onMessageChannelReady:
{
data.enforceInterface(DESCRIPTOR);
android.support.customtabs.ICustomTabsCallback _arg0;
_arg0 = android.support.customtabs.ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
android.os.Bundle _arg1;
if ((0!=data.readInt())) {
_arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
this.onMessageChannelReady(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_onPostMessage:
{
data.enforceInterface(DESCRIPTOR);
android.support.customtabs.ICustomTabsCallback _arg0;
_arg0 = android.support.customtabs.ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
java.lang.String _arg1;
_arg1 = data.readString();
android.os.Bundle _arg2;
if ((0!=data.readInt())) {
_arg2 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg2 = null;
}
this.onPostMessage(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements android.support.customtabs.IPostMessageService
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
@Override public void onMessageChannelReady(android.support.customtabs.ICustomTabsCallback callback, android.os.Bundle extras) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
if ((extras!=null)) {
_data.writeInt(1);
extras.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onMessageChannelReady, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onPostMessage(android.support.customtabs.ICustomTabsCallback callback, java.lang.String message, android.os.Bundle extras) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
_data.writeString(message);
if ((extras!=null)) {
_data.writeInt(1);
extras.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onPostMessage, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_onMessageChannelReady = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_onPostMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
public void onMessageChannelReady(android.support.customtabs.ICustomTabsCallback callback, android.os.Bundle extras) throws android.os.RemoteException;
public void onPostMessage(android.support.customtabs.ICustomTabsCallback callback, java.lang.String message, android.os.Bundle extras) throws android.os.RemoteException;
}
