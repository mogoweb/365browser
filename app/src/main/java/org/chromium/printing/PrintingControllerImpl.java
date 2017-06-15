// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.printing;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.printing.PrintDocumentAdapterWrapper.PdfGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Controls the interactions with Android framework related to printing.
 *
 * This class is singleton, since at any point at most one printing dialog can exist. Also, since
 * this dialog is modal, user can't interact with the browser unless they close the dialog or press
 * the print button. The singleton object lives in UI thread. Interaction with the native side is
 * carried through PrintingContext class.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class PrintingControllerImpl implements PrintingController, PdfGenerator {
    private static final String TAG = "cr.printing";

    /**
     * This is used for both initial state and a completed state (i.e. starting from either
     * onLayout or onWrite, a PDF generation cycle is completed another new one can safely start).
     */
    private static final int PRINTING_STATE_READY = 0;
    private static final int PRINTING_STATE_STARTED_FROM_ONLAYOUT = 1;
    private static final int PRINTING_STATE_STARTED_FROM_ONWRITE = 2;
    /** Printing dialog has been dismissed and cleanup has been done. */
    private static final int PRINTING_STATE_FINISHED = 3;

    /** The singleton instance for this class. */
    private static PrintingController sInstance;

    private final String mErrorMessage;

    private PrintingContextInterface mPrintingContext;

    /**
     * The context of a query initiated by window.print(), stored here to allow syncrhonization
     * with javascript.
     */
    private PrintingContextInterface mContextFromScriptInitiation;

    /** The file descriptor into which the PDF will be written.  Provided by the framework. */
    private int mFileDescriptor;

    /** Dots per inch, as provided by the framework. */
    private int mDpi;

    /** Paper dimensions. */
    private PrintAttributes.MediaSize mMediaSize;

    /** Numbers of pages to be printed, zero indexed. */
    private int[] mPages;

    /** The callback function to inform the result of PDF generation to the framework. */
    private PrintDocumentAdapterWrapper.WriteResultCallbackWrapper mOnWriteCallback;

    /**
     * The callback function to inform the result of layout to the framework.  We save the callback
     * because we start the native PDF generation process inside onLayout, and we need to pass the
     * number of expected pages back to the framework through this callback once the native side
     * has that information.
     */
    private PrintDocumentAdapterWrapper.LayoutResultCallbackWrapper mOnLayoutCallback;

    /** The object through which native PDF generation process is initiated. */
    private Printable mPrintable;

    /** The object through which the framework will make calls for generating PDF. */
    private PrintDocumentAdapterWrapper mPrintDocumentAdapterWrapper;

    private int mPrintingState = PRINTING_STATE_READY;

    /** Whether layouting parameters have been changed to require a new PDF generation. */
    private boolean mNeedNewPdf;

    /** Total number of pages to print with initial print dialog settings. */
    private int mLastKnownMaxPages = PrintDocumentInfo.PAGE_COUNT_UNKNOWN;

    private boolean mIsBusy;

    private PrintManagerDelegate mPrintManager;

    private PrintingControllerImpl(PrintDocumentAdapterWrapper printDocumentAdapterWrapper,
                                   String errorText) {
        mErrorMessage = errorText;
        mPrintDocumentAdapterWrapper = printDocumentAdapterWrapper;
        mPrintDocumentAdapterWrapper.setPdfGenerator(this);
    }

    /**
     * Creates a controller for handling printing with the framework.
     *
     * The controller is a singleton, since there can be only one printing action at any time.
     *
     * @param printDocumentAdapterWrapper The object through which the framework will make calls
     *                                    for generating PDF.
     * @param errorText The error message to be shown to user in case something goes wrong in PDF
     *                  generation in Chromium. We pass it here as a string so src/printing/android
     *                  doesn't need any string dependency.
     * @return The resulting PrintingController.
     */
    public static PrintingController create(
            PrintDocumentAdapterWrapper printDocumentAdapterWrapper, String errorText) {
        ThreadUtils.assertOnUiThread();

        if (sInstance == null) {
            sInstance = new PrintingControllerImpl(printDocumentAdapterWrapper, errorText);
        }
        return sInstance;
    }

    /**
     * Returns the singleton instance, created by the {@link PrintingControllerImpl#create}.
     *
     * This method must be called once {@link PrintingControllerImpl#create} is called, and always
     * thereafter.
     *
     * @return The singleton instance.
     */
    public static PrintingController getInstance() {
        return sInstance;
    }

    @Override
    public boolean hasPrintingFinished() {
        return mPrintingState == PRINTING_STATE_FINISHED;
    }

    @Override
    public int getDpi() {
        return mDpi;
    }

    @Override
    public int getFileDescriptor() {
        return mFileDescriptor;
    }

    @Override
    public int getPageHeight() {
        return mMediaSize.getHeightMils();
    }

    @Override
    public int getPageWidth() {
        return mMediaSize.getWidthMils();
    }

    @Override
    public int[] getPageNumbers() {
        return mPages == null ? null : mPages.clone();
    }

    @Override
    public boolean isBusy() {
        return mIsBusy;
    }

    @Override
    public void setPrintingContext(final PrintingContextInterface printingContext) {
        mPrintingContext = printingContext;
    }

    @Override
    public void setPendingPrint(final Printable printable, PrintManagerDelegate printManager) {
        if (mIsBusy) {
            Log.d(TAG, "Pending print can't be set. PrintingController is busy.");
            return;
        }
        mPrintable = printable;
        mPrintManager = printManager;
    }

    @Override
    public void startPendingPrint(PrintingContextInterface printingContext) {
        if (mIsBusy || mPrintManager == null) {
            if (mIsBusy) Log.d(TAG, "Pending print can't be started. PrintingController is busy.");
            else Log.d(TAG, "Pending print can't be started. No PrintManager provided.");

            if (printingContext != null) printingContext.showSystemDialogDone();
            return;
        }
        mContextFromScriptInitiation = printingContext;
        mIsBusy = true;
        mPrintDocumentAdapterWrapper.print(mPrintManager, mPrintable.getTitle());
        mPrintManager = null;
    }

    @Override
    public void startPrint(final Printable printable, PrintManagerDelegate printManager) {
        if (mIsBusy) return;
        setPendingPrint(printable, printManager);
        startPendingPrint(null);
    }

    @Override
    public void pdfWritingDone(boolean success) {
        if (mPrintingState == PRINTING_STATE_FINISHED) return;
        mPrintingState = PRINTING_STATE_READY;
        if (success) {
            PageRange[] pageRanges = convertIntegerArrayToPageRanges(mPages);
            mOnWriteCallback.onWriteFinished(pageRanges);
        } else {
            mOnWriteCallback.onWriteFailed(mErrorMessage);
            resetCallbacks();
        }
        closeFileDescriptor(mFileDescriptor);
        mFileDescriptor = -1;
    }

    @Override
    public void onStart() {
        mPrintingState = PRINTING_STATE_READY;
    }

    @Override
    public void onLayout(
            PrintAttributes oldAttributes,
            PrintAttributes newAttributes,
            CancellationSignal cancellationSignal,
            PrintDocumentAdapterWrapper.LayoutResultCallbackWrapper callback,
            Bundle metadata) {
        // NOTE: Chrome printing just supports one DPI, whereas Android has both vertical and
        // horizontal.  These two values are most of the time same, so we just pass one of them.
        mDpi = newAttributes.getResolution().getHorizontalDpi();
        mMediaSize = newAttributes.getMediaSize();

        mNeedNewPdf = !newAttributes.equals(oldAttributes);

        mOnLayoutCallback = callback;
        // We don't want to stack Chromium with multiple PDF generation operations before
        // completion of an ongoing one.
        // TODO(cimamoglu): Whenever onLayout is called, generate a new PDF with the new
        //                  parameters. Hence, we can get the true number of pages.
        if (mPrintingState == PRINTING_STATE_STARTED_FROM_ONLAYOUT) {
            // We don't start a new Chromium PDF generation operation if there's an existing
            // onLayout going on. Use the last known valid page count.
            pageCountEstimationDone(mLastKnownMaxPages);
        } else if (mPrintingState == PRINTING_STATE_STARTED_FROM_ONWRITE) {
            callback.onLayoutFailed(mErrorMessage);
            resetCallbacks();
        } else if (mPrintable.print()) {
            mPrintingState = PRINTING_STATE_STARTED_FROM_ONLAYOUT;
        } else {
            callback.onLayoutFailed(mErrorMessage);
            resetCallbacks();
        }
    }

    @Override
    public void pageCountEstimationDone(final int maxPages) {
        // This method might be called even after onFinish, e.g. as a result of a long page
        // estimation operation.  We make sure that such call has no effect, since the printing
        // dialog has already been dismissed and relevant cleanup has already been done.
        // Also, this ensures that we do not call askUserForSettingsReply twice.
        if (mPrintingState == PRINTING_STATE_FINISHED) return;
        if (maxPages != PrintDocumentInfo.PAGE_COUNT_UNKNOWN) {
            mLastKnownMaxPages = maxPages;
        }
        if (mPrintingState == PRINTING_STATE_STARTED_FROM_ONLAYOUT) {
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(mPrintable.getTitle())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(mLastKnownMaxPages)
                    .build();
            mOnLayoutCallback.onLayoutFinished(info, mNeedNewPdf);
        } else if (mPrintingState == PRINTING_STATE_STARTED_FROM_ONWRITE) {
            // Chromium PDF generation is started inside onWrite, continue that.
            if (mPrintingContext == null) {
                mOnWriteCallback.onWriteFailed(mErrorMessage);
                resetCallbacks();
                return;
            }
            mPrintingContext.askUserForSettingsReply(true);
        }
    }

    @Override
    public void onWrite(
            final PageRange[] ranges,
            final ParcelFileDescriptor destination,
            final CancellationSignal cancellationSignal,
            final PrintDocumentAdapterWrapper.WriteResultCallbackWrapper callback) {
        if (mPrintingContext == null) {
            callback.onWriteFailed(mErrorMessage);
            resetCallbacks();
            return;
        }

        // TODO(cimamoglu): Make use of CancellationSignal.
        mOnWriteCallback = callback;

        mFileDescriptor = destination.getFd();
        // Update file descriptor to PrintingContext mapping in the owner class.
        mPrintingContext.updatePrintingContextMap(mFileDescriptor, false);

        // We need to convert ranges list into an array of individual numbers for
        // easier JNI passing and compatibility with the native side.
        if (ranges.length == 1 && ranges[0].equals(PageRange.ALL_PAGES)) {
            // null corresponds to all pages in Chromium printing logic.
            mPages = null;
        } else {
            mPages = normalizeRanges(ranges);
        }

        if (mPrintingState == PRINTING_STATE_READY) {
            // If this onWrite is without a preceding onLayout, start Chromium PDF generation here.
            if (mPrintable.print()) {
                mPrintingState = PRINTING_STATE_STARTED_FROM_ONWRITE;
            } else {
                callback.onWriteFailed(mErrorMessage);
                resetCallbacks();
            }
        } else if (mPrintingState == PRINTING_STATE_STARTED_FROM_ONLAYOUT) {
            // Otherwise, continue previously started operation.
            mPrintingContext.askUserForSettingsReply(true);
        }
        // We are guaranteed by the framework that we will not have two onWrite calls at once.
        // We may get a CancellationSignal, after replying it (via WriteResultCallback) we might
        // get another onWrite call.
    }

    @Override
    public void onFinish() {
        mLastKnownMaxPages = PrintDocumentInfo.PAGE_COUNT_UNKNOWN;
        mPages = null;

        if (mPrintingContext != null) {
            if (mPrintingState != PRINTING_STATE_READY) {
                // Note that we are never making an extraneous askUserForSettingsReply call.
                // If we are in the middle of a PDF generation from onLayout or onWrite, it means
                // the state isn't PRINTING_STATE_READY, so we enter here and make this call (no
                // extra). If we complete the PDF generation successfully from onLayout or onWrite,
                // we already make the state PRINTING_STATE_READY and call askUserForSettingsReply
                // inside pdfWritingDone, thus not entering here.  Also, if we get an extra
                // AskUserForSettings call, it's handled inside {@link
                // PrintingContext#pageCountEstimationDone}.
                mPrintingContext.askUserForSettingsReply(false);
            }
            mPrintingContext.updatePrintingContextMap(mFileDescriptor, true);
            mPrintingContext = null;
        }

        if (mContextFromScriptInitiation != null) {
            mContextFromScriptInitiation.showSystemDialogDone();
            mContextFromScriptInitiation = null;
        }

        mPrintingState = PRINTING_STATE_FINISHED;

        closeFileDescriptor(mFileDescriptor);
        mFileDescriptor = -1;

        resetCallbacks();
        // The printmanager contract is that onFinish() is always called as the last
        // callback. We set busy to false here.
        mIsBusy = false;
    }

    private void resetCallbacks() {
        mOnWriteCallback = null;
        mOnLayoutCallback = null;
    }

    private static void closeFileDescriptor(int fd) {
        if (fd != -1) return;
        ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.adoptFd(fd);
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException ioe) {
                /* ignore */
            }
        }
    }

    private static PageRange[] convertIntegerArrayToPageRanges(int[] pagesArray) {
        PageRange[] pageRanges;
        if (pagesArray != null) {
            pageRanges = new PageRange[pagesArray.length];
            for (int i = 0; i < pageRanges.length; i++) {
                int page = pagesArray[i];
                pageRanges[i] = new PageRange(page, page);
            }
        } else {
            // null corresponds to all pages in Chromium printing logic.
            pageRanges = new PageRange[] { PageRange.ALL_PAGES };
        }
        return pageRanges;
    }

    /**
     * Gets an array of page ranges and returns an array of integers with all ranges expanded.
     */
    private static int[] normalizeRanges(final PageRange[] ranges) {
        // Expand ranges into a list of individual numbers.
        ArrayList<Integer> pages = new ArrayList<Integer>();
        for (PageRange range : ranges) {
            for (int i = range.getStart(); i <= range.getEnd(); i++) {
                pages.add(i);
            }
        }

        // Convert the list into array.
        int[] ret = new int[pages.size()];
        Iterator<Integer> iterator = pages.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next().intValue();
        }
        return ret;
    }
}
