/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/custom_tabs_client/src/customtabs/src/android/support/customtabs/ICustomTabsService.aidl
 */
package android.support.customtabs;
/**
 * Interface to a CustomTabsService.
 * @hide
 */
public interface ICustomTabsService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements android.support.customtabs.ICustomTabsService
{
private static final java.lang.String DESCRIPTOR = "android.support.customtabs.ICustomTabsService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an android.support.customtabs.ICustomTabsService interface,
 * generating a proxy if needed.
 */
public static android.support.customtabs.ICustomTabsService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof android.support.customtabs.ICustomTabsService))) {
return ((android.support.customtabs.ICustomTabsService)iin);
}
return new android.support.customtabs.ICustomTabsService.Stub.Proxy(obj);
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
case TRANSACTION_warmup:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
boolean _result = this.warmup(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_newSession:
{
data.enforceInterface(DESCRIPTOR);
android.support.customtabs.ICustomTabsCallback _arg0;
_arg0 = android.support.customtabs.ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
boolean _result = this.newSession(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_mayLaunchUrl:
{
data.enforceInterface(DESCRIPTOR);
android.support.customtabs.ICustomTabsCallback _arg0;
_arg0 = android.support.customtabs.ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
android.net.Uri _arg1;
if ((0!=data.readInt())) {
_arg1 = android.net.Uri.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
android.os.Bundle _arg2;
if ((0!=data.readInt())) {
_arg2 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg2 = null;
}
java.util.List<android.os.Bundle> _arg3;
_arg3 = data.createTypedArrayList(android.os.Bundle.CREATOR);
boolean _result = this.mayLaunchUrl(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_extraCommand:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
android.os.Bundle _arg1;
if ((0!=data.readInt())) {
_arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
android.os.Bundle _result = this.extraCommand(_arg0, _arg1);
reply.writeNoException();
if ((_result!=null)) {
reply.writeInt(1);
_result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_updateVisuals:
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
boolean _result = this.updateVisuals(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_requestPostMessageChannel:
{
data.enforceInterface(DESCRIPTOR);
android.support.customtabs.ICustomTabsCallback _arg0;
_arg0 = android.support.customtabs.ICustomTabsCallback.Stub.asInterface(data.readStrongBinder());
android.net.Uri _arg1;
if ((0!=data.readInt())) {
_arg1 = android.net.Uri.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
boolean _result = this.requestPostMessageChannel(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_postMessage:
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
int _result = this.postMessage(_arg0, _arg1, _arg2);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements android.support.customtabs.ICustomTabsService
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
@Override public boolean warmup(long flags) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(flags);
mRemote.transact(Stub.TRANSACTION_warmup, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public boolean newSession(android.support.customtabs.ICustomTabsCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_newSession, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public boolean mayLaunchUrl(android.support.customtabs.ICustomTabsCallback callback, android.net.Uri url, android.os.Bundle extras, java.util.List<android.os.Bundle> otherLikelyBundles) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
if ((url!=null)) {
_data.writeInt(1);
url.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
if ((extras!=null)) {
_data.writeInt(1);
extras.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeTypedList(otherLikelyBundles);
mRemote.transact(Stub.TRANSACTION_mayLaunchUrl, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public android.os.Bundle extraCommand(java.lang.String commandName, android.os.Bundle args) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
android.os.Bundle _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(commandName);
if ((args!=null)) {
_data.writeInt(1);
args.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_extraCommand, _data, _reply, 0);
_reply.readException();
if ((0!=_reply.readInt())) {
_result = android.os.Bundle.CREATOR.createFromParcel(_reply);
}
else {
_result = null;
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public boolean updateVisuals(android.support.customtabs.ICustomTabsCallback callback, android.os.Bundle bundle) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
if ((bundle!=null)) {
_data.writeInt(1);
bundle.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_updateVisuals, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public boolean requestPostMessageChannel(android.support.customtabs.ICustomTabsCallback callback, android.net.Uri postMessageOrigin) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
if ((postMessageOrigin!=null)) {
_data.writeInt(1);
postMessageOrigin.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_requestPostMessageChannel, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int postMessage(android.support.customtabs.ICustomTabsCallback callback, java.lang.String message, android.os.Bundle extras) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
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
mRemote.transact(Stub.TRANSACTION_postMessage, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_warmup = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_newSession = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_mayLaunchUrl = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_extraCommand = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_updateVisuals = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_requestPostMessageChannel = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_postMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
}
public boolean warmup(long flags) throws android.os.RemoteException;
public boolean newSession(android.support.customtabs.ICustomTabsCallback callback) throws android.os.RemoteException;
public boolean mayLaunchUrl(android.support.customtabs.ICustomTabsCallback callback, android.net.Uri url, android.os.Bundle extras, java.util.List<android.os.Bundle> otherLikelyBundles) throws android.os.RemoteException;
public android.os.Bundle extraCommand(java.lang.String commandName, android.os.Bundle args) throws android.os.RemoteException;
public boolean updateVisuals(android.support.customtabs.ICustomTabsCallback callback, android.os.Bundle bundle) throws android.os.RemoteException;
public boolean requestPostMessageChannel(android.support.customtabs.ICustomTabsCallback callback, android.net.Uri postMessageOrigin) throws android.os.RemoteException;
public int postMessage(android.support.customtabs.ICustomTabsCallback callback, java.lang.String message, android.os.Bundle extras) throws android.os.RemoteException;
}
