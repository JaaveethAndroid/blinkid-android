package com.microblink.blinkid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.microblink.Config;
import com.microblink.activity.BlinkOCRActivity;
import com.microblink.activity.Pdf417ScanActivity;
import com.microblink.activity.ScanActivity;
import com.microblink.activity.ScanCard;
import com.microblink.activity.ShowOcrResultMode;
import com.microblink.help.HelpActivity;
import com.microblink.libresult.ResultActivity;
import com.microblink.ocr.ScanConfiguration;
import com.microblink.recognizers.BaseRecognitionResult;
import com.microblink.recognizers.RecognitionResults;
import com.microblink.recognizers.blinkbarcode.bardecoder.BarDecoderRecognizerSettings;
import com.microblink.recognizers.blinkbarcode.pdf417.Pdf417RecognizerSettings;
import com.microblink.recognizers.blinkbarcode.usdl.USDLRecognizerSettings;
import com.microblink.recognizers.blinkbarcode.zxing.ZXingRecognizerSettings;
import com.microblink.recognizers.blinkid.malaysia.MyKadRecognizerSettings;
import com.microblink.recognizers.blinkid.mrtd.MRTDRecognizerSettings;
import com.microblink.recognizers.blinkid.ukdl.UKDLRecognizerSettings;
import com.microblink.recognizers.blinkocr.parser.generic.AmountParserSettings;
import com.microblink.recognizers.blinkocr.parser.generic.IbanParserSettings;
import com.microblink.recognizers.blinkocr.parser.generic.RawParserSettings;
import com.microblink.recognizers.settings.RecognitionSettings;
import com.microblink.recognizers.settings.RecognizerSettings;
import com.microblink.util.Log;
import com.microblink.util.RecognizerCompatibility;
import com.microblink.util.RecognizerCompatibilityStatus;

import java.util.ArrayList;

public class MenuActivity extends Activity {

    public static final int MY_BLINKID_REQUEST_CODE = 0x101;

    private ListElement[] mElements;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // use this to change BlinkID language. Device default is used by default.
//        LanguageUtils.setLanguage(Language.English, this);

        // in case of problems with the SDK (crashes or ANRs, uncomment following line to enable
        // verbose logging that can help developers track down the problem)
//        Log.setLogLevel(Log.LogLevel.LOG_VERBOSE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // check if BlinkID is supported on the device
        RecognizerCompatibilityStatus supportStatus = RecognizerCompatibility.getRecognizerCompatibilityStatus(this);
        if (supportStatus != RecognizerCompatibilityStatus.RECOGNIZER_SUPPORTED) {
            Toast.makeText(this, "BlinkID is not supported! Reason: " + supportStatus.name(), Toast.LENGTH_LONG).show();
        }

        // build list elements
        buildElements();
        ListView lv = (ListView) findViewById(R.id.recognizerList);
        ArrayAdapter<ListElement> listAdapter = new ArrayAdapter<ListElement>(this, android.R.layout.simple_list_item_1, mElements);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startActivityForResult(mElements[position].getScanIntent(), MY_BLINKID_REQUEST_CODE);
            }
        });
    }

    /**
     * This method is invoked after returning from scan activity. You can obtain
     * scan results here
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // onActivityResult is called whenever we are returned from activity started
        // with startActivityForResult. We need to check request code to determine
        // that we have really returned from BlinkID activity.
        if (requestCode == MY_BLINKID_REQUEST_CODE) {

            // make sure BlinkID activity returned result
            if (resultCode == ScanActivity.RESULT_OK && data != null) {

                // depending on settings, we may have multiple scan results.
                // we first need to obtain list of recognition results
                Bundle extras = data.getExtras();
                RecognitionResults results = data.getParcelableExtra(ScanActivity.EXTRAS_RECOGNITION_RESULTS);
                BaseRecognitionResult[] resArray = null;
                if (results != null) {
                    // get array of recognition results
                    resArray = results.getRecognitionResults();
                }
                if (resArray != null) {
                    Log.i(this, "Data count: " + resArray.length);
                    int i = 1;

                    for (BaseRecognitionResult res : resArray) {
                        Log.i(this, "Data #" + Integer.valueOf(i++).toString());

                        // Each element in resultArray inherits BaseRecognitionResult class and
                        // represents the scan result of one of activated recognizers that have
                        // been set up.

                        res.log();
                    }
                } else {
                    Log.e(this, "Unable to retrieve recognition results!");
                }

                // set intent's component to ResultActivity and pass its contents
                // to ResultActivity. ResultActivity will show how to extract
                // data from result.

                data.setComponent(new ComponentName(this, ResultActivity.class));
                startActivity(data);
            } else {
                // if BlinkID activity did not return result, user has probably
                // pressed Back button and cancelled scanning
                Toast.makeText(this, "Scan cancelled!", Toast.LENGTH_SHORT).show();
            }
        }
    }


    /**
     * This method will build scan intent for BlinkID. Method needs array of recognizer settings
     * to know which recognizers to enable, activity to which intent will be sent and optionally
     * an intent for HelpActivity that will be used if user taps the Help button on scan activity.
     */
    private Intent buildIntent(RecognizerSettings[] settArray, Class<?> target, Intent helpIntent) {
        // first create intent for given activity
        final Intent intent = new Intent(this, target);

        // optionally, if you want the beep sound to be played after a scan
        // add a sound resource id as EXTRAS_BEEP_RESOURCE extra
        intent.putExtra(ScanActivity.EXTRAS_BEEP_RESOURCE, R.raw.beep);

        // if we have help intent, we can pass it to scan activity so it can invoke
        // it if user taps the help button. If we do not set the help intent,
        // scan activity will hide the help button.
        if (helpIntent != null) {
            intent.putExtra(ScanActivity.EXTRAS_HELP_INTENT, helpIntent);
        }

        // prepare the recognition settings
        RecognitionSettings settings = new RecognitionSettings();

        // with setNumMsBeforeTimeout you can define number of miliseconds that must pass
        // after first partial scan result has arrived before scan activity triggers a timeout.
        // Timeout is good for preventing infinitely long scanning experience when user attempts
        // to scan damaged or unsupported slip. After timeout, scan activity will return only
        // data that was read successfully. This might be incomplete data.
        settings.setNumMsBeforeTimeout(1000);

        // If you add more recognizers to recognizer settings array, you can choose whether you
        // want to have the ability to obtain multiple scan results from same video frame. For example,
        // if both payment slip and payment barcode are visible on a single frame, by setting
        // setAllowMultipleScanResultsOnSingleImage to true you can obtain both scan results
        // from barcode and slip. If this is false (default), you will get the first valid result
        // (i.e. first result that contains all required data). Having this option turned off
        // creates better and faster user experience.
//        settings.setAllowMultipleScanResultsOnSingleImage(true);

        // now add array with recognizer settings so that scan activity will know
        // what do you want to scan. Setting recognizer settings array is mandatory.
        settings.setRecognizerSettingsArray(settArray);
        intent.putExtra(ScanActivity.EXTRAS_RECOGNITION_SETTINGS, settings);

        // In order for scanning to work, you must enter a valid licence key. Without licence key,
        // scanning will not work. Licence key is bound the the package name of your app, so when
        // obtaining your licence key from Microblink make sure you give us the correct package name
        // of your app. You can obtain your licence key at http://microblink.com/login or contact us
        // at http://help.microblink.com.
        // Licence key also defines which recognizers are enabled and which are not. Since the licence
        // key validation is performed on image processing thread in native code, all enabled recognizers
        // that are disallowed by licence key will be turned off without any error and information
        // about turning them off will be logged to ADB logcat.
        intent.putExtra(ScanActivity.EXTRAS_LICENSE_KEY, Config.LICENSE_KEY);

        // If you want, you can disable drawing of OCR results on scan activity. Drawing OCR results can be visually
        // appealing and might entertain the user while waiting for scan to complete, but might introduce a small
        // performance penalty.
        // intent.putExtra(ScanActivity.EXTRAS_SHOW_OCR_RESULT, false);

        /// If you want you can have scan activity display the focus rectangle whenever camera
        // attempts to focus, similarly to various camera app's touch to focus effect.
        // By default this is off, and you can turn this on by setting EXTRAS_SHOW_FOCUS_RECTANGLE
        // extra to true.
        intent.putExtra(ScanActivity.EXTRAS_SHOW_FOCUS_RECTANGLE, true);

        // If you want, you can enable the pinch to zoom feature of scan activity.
        // By enabling this you allow the user to use the pinch gesture to zoom the camera.
        // By default this is off and can be enabled by setting EXTRAS_ALLOW_PINCH_TO_ZOOM extra to true.
        intent.putExtra(ScanActivity.EXTRAS_ALLOW_PINCH_TO_ZOOM, true);

        // Enable showing of OCR results as animated dots. This does not have effect if non-OCR recognizer like
        // barcode recognizer is active.
        intent.putExtra(BlinkOCRActivity.EXTRAS_SHOW_OCR_RESULT_MODE, (Parcelable) ShowOcrResultMode.ANIMATED_DOTS);

        return intent;
    }

    /**
     * Builds intent for segment scan.
     * @param configArray Array of scan configurations. Each scan configuration
     *          contains 4 elements: resource ID for title displayed
     *          in BlinkOCRActivity activity, resource ID for text
     *          displayed in activity, name of the scan element (used
     *          for obtaining results) and parser setting defining
     *          how the data will be extracted.
     * @return Built intent for segment scan.
     */
    private Intent buildSegmentScanIntent(ScanConfiguration[] configArray) {
        final Intent intent = new Intent(this, BlinkOCRActivity.class);

        // configure help activity to display help for segment scan
        Intent helpIntent = new Intent(this, HelpActivity.class);
        intent.putExtra(BlinkOCRActivity.EXTRAS_HELP_INTENT, helpIntent);

        intent.putExtra(BlinkOCRActivity.EXTRAS_SCAN_CONFIGURATION, configArray);
        intent.putExtra(BlinkOCRActivity.EXTRAS_LICENSE_KEY, Config.LICENSE_KEY);

        intent.putExtra(BlinkOCRActivity.EXTRAS_SHOW_OCR_RESULT_MODE, (Parcelable) ShowOcrResultMode.ANIMATED_DOTS);

        return intent;
    }

    /**
     * This method is used to build the array of ListElement objects. Each ListElement
     * object will have its title that will be shown in ListView and prepared intent
     * for BlinkID.
     */
    private void buildElements() {
        ArrayList<ListElement> elements = new ArrayList<ListElement>();

        // ID document list entry
        elements.add(buildMrtdElement());
        elements.add(buildUkdlElement());
        elements.add(buildUsdlElement());
        // currently disabled in Demo
//        elements.add(buildMyKadElement());

        // barcode list entries
        elements.add(buildPDF417Element());
        elements.add(buildBardecoderElement());
        elements.add(buildZXingElement());

        // Blink OCR entries
        elements.add(buildGenericSegmentScanElement());

        mElements = new ListElement[elements.size()];
        elements.toArray(mElements);
    }

    private ListElement buildMrtdElement() {
        // prepare settings for Machine Readable Travel Document (MRTD) recognizer
        MRTDRecognizerSettings mrtd = new MRTDRecognizerSettings();

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning ID documents, we will use ScanCard activity which has more suitable UI for scanning ID documents
        return new ListElement("ID document", buildIntent(new RecognizerSettings[]{mrtd}, ScanCard.class, null));
    }

    private ListElement buildUkdlElement() {
        // prepare settings for United Kingdom Driver's Licence recognizer
        UKDLRecognizerSettings ukdl = new UKDLRecognizerSettings();

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning ID documents, we will use ScanCard activity which has more suitable UI for scanning ID document
        return new ListElement("UK Driver's Licence", buildIntent(new RecognizerSettings[]{ukdl}, ScanCard.class, null));
    }

    private ListElement buildMyKadElement() {
        // prepare settings for Malaysian MyKad ID document recognizer
        MyKadRecognizerSettings myKad = new MyKadRecognizerSettings();

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning MyKad documents, we will use ScanCard activity which has more suitable UI for scanning ID document
        return new ListElement("Malaysian MyKad document", buildIntent(new RecognizerSettings[]{myKad}, ScanCard.class, null));
    }

    private ListElement buildUsdlElement() {
        USDLRecognizerSettings usdl = new USDLRecognizerSettings();

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning ID documents, we will use ScanCard activity which has more suitable UI for scanning ID document
        return new ListElement("US Driver's License", buildIntent(new RecognizerSettings[]{usdl}, ScanCard.class, null));
    }

    private ListElement buildPDF417Element() {
        // prepare settings for PDF417 barcode recognizer
        Pdf417RecognizerSettings pdf417 = new Pdf417RecognizerSettings();

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning barcodes, we will use Pdf417ScanActivity which has more suitable UI for scanning barcodes
        return new ListElement("PDF417 barcode", buildIntent(new RecognizerSettings[]{pdf417}, Pdf417ScanActivity.class, null));
    }

    private ListElement buildBardecoderElement() {
        // prepare settings for 1D barcode recognizer
        BarDecoderRecognizerSettings bar1d = new BarDecoderRecognizerSettings();
        // enable code39 and code128 barcodes
        bar1d.setScanCode128(true);
        bar1d.setScanCode39(true);

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning barcodes, we will use Pdf417ScanActivity which has more suitable UI for scanning barcodes
        return new ListElement("1D barcode", buildIntent(new RecognizerSettings[]{bar1d}, Pdf417ScanActivity.class, null));
    }

    private ListElement buildZXingElement() {
        // prepare settings for ZXing barcode recognizer
        ZXingRecognizerSettings zxing = new ZXingRecognizerSettings();
        // enable scanning of QR codes
        zxing.setScanQRCode(true);

        // build a scan intent by adding intent extras common to all other recognizers
        // when scanning barcodes, we will use Pdf417ScanActivity which has more suitable UI for scanning barcodes
        return new ListElement("QR code", buildIntent(new RecognizerSettings[]{zxing}, Pdf417ScanActivity.class, null));
    }

    private ListElement buildGenericSegmentScanElement() {
        ScanConfiguration[] conf = new ScanConfiguration[]{
                new ScanConfiguration(R.string.amount_title, R.string.amount_msg, "Amount", new AmountParserSettings()),
                new ScanConfiguration(R.string.iban_title, R.string.iban_msg, "IBAN", new IbanParserSettings()),
                new ScanConfiguration(R.string.raw_title, R.string.raw_msg, "PaymentDescription", new RawParserSettings())
        };
        return new ListElement("Segment scan", buildSegmentScanIntent(conf));
    }

    /**
     * Element of {@link ArrayAdapter} for {@link ListView} that holds information about title
     * which should be displayed in list and {@link Intent} that should be started on click.
     */
    private class ListElement {
        private String mTitle;
        private Intent mScanIntent;

        public String getTitle() {
            return mTitle;
        }

        public Intent getScanIntent() {
            return mScanIntent;
        }

        public ListElement(String title, Intent scanIntent) {
            mTitle = title;
            mScanIntent = scanIntent;
        }

        /**
         * Used by array adapter to determine list element text
         */
        @Override
        public String toString() {
            return getTitle();
        }
    }
}