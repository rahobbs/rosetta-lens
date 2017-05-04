/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rahobbs.pictonary;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.rahobbs.pictonary.Constants.API_KEY;

public class MainActivity extends AppCompatActivity {
    public static final String FILE_NAME = "temp.jpg";

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;
    public static final int LANGUAGE_PICKER_REQUEST = 4;

    private Camera mCamera;
    private CameraPreview mPreview;
    private Bitmap mBitmap;
    private Bitmap mScaledBitmap;

    private TextView mImageDetails;
    private ImageView mImageView;
    private ImageView mShutterButton;
    private ImageView mGalleryButton;
    private Boolean mIsPreviewMode = true;

    private String mTargetLanguage = "Spanish";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupLanguageSpinner();
        setupCameraPreview();

        mImageDetails = (TextView) findViewById(R.id.image_details);
        mShutterButton = (ImageView) findViewById(R.id.shutter_button);
        mGalleryButton = (ImageView) findViewById(R.id.gallery_button);

        mShutterButton.setOnClickListener(

                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mIsPreviewMode == false) {
                            transitiontoCameraView();
                        }
                        startCamera();
                    }
                }
        );

        mGalleryButton.setOnClickListener(

                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder
                                .setMessage(R.string.dialog_select_prompt)
                                .setPositiveButton(R.string.dialog_select_gallery, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        startGalleryChooser();
                                    }
                                })
                                .setNegativeButton(R.string.dialog_select_camera, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        startCamera();
                                    }
                                });
                        builder.create().show();
                    }
                });
    }

    private void setupCameraPreview() {
        // Create an instance of Camera
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);

        preview.addView(mPreview);
    }

    private void setupLanguageSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.language_spinner);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                mTargetLanguage = parentView.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.lang_array, R.layout.language_spinner);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void startGalleryChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                GALLERY_IMAGE_REQUEST);
    }

    public void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {

            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    File pictureFile = getOutputMediaFile();
                    if (pictureFile == null) {
                        return;
                    }
                    try {
                        FileOutputStream fos = new FileOutputStream(pictureFile);
                        fos.write(data);
                        fos.close();
                        mBitmap = BitmapFactory.decodeFile(pictureFile.getAbsolutePath());

                        uploadImage(Uri.fromFile(pictureFile));

                        transitionToResponseView();

                    } catch (FileNotFoundException e) {

                    } catch (IOException e) {
                    }
                }
            });
        }
    }

    private void transitionToResponseView() {
        mPreview.setVisibility(View.INVISIBLE);

        mImageView = (ImageView) findViewById(R.id.image_view);
        mImageView.setImageBitmap(mBitmap);
        mShutterButton.setImageResource(R.drawable.ic_photo_camera_black_24dp);
        mGalleryButton.setImageResource(R.drawable.gallery_panorama_black);
        mIsPreviewMode = false;
    }

    public void transitiontoCameraView() {
        mImageView.setVisibility(View.INVISIBLE);
        mImageDetails.setText("");
        mShutterButton.setImageResource(R.drawable.shutter_white);
        mGalleryButton.setImageResource(R.drawable.gallery_panorama_white);
        setupCameraPreview();
        mIsPreviewMode = true;

    }

    private static File getOutputMediaFile() {
        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Rosetta_Lens");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("Rosetta_Lens", "failed to create directory");
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
                .format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "IMG_" + timeStamp + ".jpg");

        return mediaFile;
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            Camera.Parameters parameters = c.getParameters();
            parameters.set("jpeg-quality", 70);
            parameters.setPictureFormat(PixelFormat.JPEG);
            parameters.setPictureSize(2048, 1232);
            //parameters.setRotation(90);

            c.setParameters(parameters);
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public File getCameraFile() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LANGUAGE_PICKER_REQUEST && resultCode == RESULT_OK) {
            //Set Target Language
            Log.e("target lang", data.getStringExtra("result"));
            mTargetLanguage = data.getStringExtra("result");
        } else if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK) {
            uploadImage(data.getData());
            transitionToResponseView();
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            uploadImage(Uri.fromFile(getCameraFile()));
            transitionToResponseView();


        } else {
            setupCameraPreview();
        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils.permissionGranted(
                requestCode,
                CAMERA_PERMISSIONS_REQUEST,
                grantResults)) {
            startCamera();
        }
    }

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                // scale the image to save on bandwidth
                mScaledBitmap =
                        scaleBitmapDown(mBitmap, 1200);


                mPreview.setVisibility(View.INVISIBLE);
                ImageView mImageView = (ImageView) findViewById(R.id.image_view);
                mImageView.setImageBitmap(mBitmap);

                callCloudVision(mScaledBitmap);


            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    private void callCloudVision(final Bitmap bitmap) throws IOException {
        // Switch text to loading
        mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(new
                            VisionRequestInitializer(API_KEY));
                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                            new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                        // Add the image
                        Image base64EncodedImage = new Image();
                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // add the features we want
                        annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                            Feature labelDetection = new Feature();
                            labelDetection.setType("LABEL_DETECTION");
                            labelDetection.setMaxResults(3);
                            add(labelDetection);
                        }});

                        // Add the list of one thing to the request
                        add(annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest =
                            vision.images().annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d(TAG, "created Cloud Vision request object, sending request");

                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);

                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " +
                            e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                mImageDetails.setText(result);
            }
        }.execute();
    }

    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "I found these things to translate:\n\n";

        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            for (EntityAnnotation label : labels) {
                message += (label.getDescription() + " => " + translateText(label.getDescription()));
                message += "\n";

            }
        } else {
            message += "nothing";
        }

        return message;
    }

    public String translateText(String toTranslate) {
        // Instantiates a client
        Translate translate = TranslateOptions.newBuilder().setApiKey(API_KEY).build().getService();

        // Translates some text from English into target language
        Translation translation = translate.translate(
                toTranslate,
                TranslateOption.sourceLanguage("en"),
                TranslateOption.targetLanguage(getLanguageCode(mTargetLanguage))
        );

        return translation.getTranslatedText();
    }

    public String getLanguageCode(String languageCode){
        switch(languageCode) {
            case "Spanish": return "es";
            case "French": return "fr";
            case "Japanese": return "ja";
            case "Korean": return "ko";
            case "German": return "de";
            case "Russian": return "ru";
            default: return "Invalid language";
        }
    }
}
