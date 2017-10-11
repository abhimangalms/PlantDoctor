/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;
// Explicit import needed for internal Google builds.

public class ClassifierActivity extends Activity {


  // These are the settings for the original v1 Inception model. If you want to
  // use a model that's been produced from the TensorFlow for Poets codelab,
  // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
  // INPUT_NAME = "Mul", and OUTPUT_NAME = "final_result".
  // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
  // the ones you produced.
  //
  // To use v3 Inception model, strip the DecodeJpeg Op from your retrained
  // model first:
  //
  // python strip_unused.py \
  // --input_graph=<retrained-pb-file> \
  // --output_graph=<your-stripped-pb-file> \
  // --input_node_names="Mul" \
  // --output_node_names="final_result" \
  // --input_binary=true
  private static final int INPUT_SIZE = 224;
  private static final int IMAGE_MEAN = 117;
  private static final float IMAGE_STD = 1;
  private static final String INPUT_NAME = "input";
  //private static final String OUTPUT_NAME = "output";
  private static final String OUTPUT_NAME = "final_result";


//  private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
//  private static final String LABEL_FILE =
//      "file:///android_asset/imagenet_comp_graph_label_strings.txt";
  private static final String MODEL_FILE = "file:///android_asset/retrained_graph.pb";
  private static final String LABEL_FILE =
          "file:///android_asset/retrained_labels.txt";


  private Classifier classifier;


  private Button mButtonSelectPhoto;
  private Button mButtonTakePhoto;
  private Button mButtonDiagnose;
  private ImageView mImageView;
  private TextView mTextView;
  private File temFile;
  private Bitmap mDiagnosisBitmap;

  private static final int PHOTO_REQUEST_CAMERA = 1;
  private static final int PHOTO_REQUEST_GALLERY = 2;
  private static final int PHOTO_REQUEST_CUT = 3;
  private static final int PHOTO_REQUEST_CODE = 4;
  private static final int PERMISSION_REQUEST_CODE = 5;
  private static final String PHOTO_FILE_NAME = "temp_photo.jpg";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_classifier);

    final AssetManager assetManager = getAssets();
    classifier = TensorFlowImageClassifier.create(assetManager,MODEL_FILE,LABEL_FILE,INPUT_SIZE,IMAGE_MEAN,IMAGE_STD,INPUT_NAME,OUTPUT_NAME);


    mButtonSelectPhoto = (Button) findViewById(R.id.buttonSelectPhoto);
    mButtonTakePhoto = (Button) findViewById(R.id.buttonTakePhotoPhoto);
    mButtonDiagnose = (Button) findViewById(R.id.buttonDiagnose);
    mImageView = (ImageView) findViewById(R.id.image);
    mTextView = (TextView) findViewById(R.id.textClassificationResult);

    mButtonTakePhoto.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          if (checkSelfPermission(Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PHOTO_REQUEST_CODE);
          }
          else
            camera(view);
        }
      }
    });
    mButtonSelectPhoto.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        gallery(view);
      }
    });

    mButtonDiagnose.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        final List<Classifier.Recognition> results = classifier.recognizeImage(mDiagnosisBitmap);
        System.out.println(results.size());
        for (final Classifier.Recognition result: results){
          System.out.println("Result:"+result.getTitle()+" "+result.getConfidence()+result.toString());

        }
        mTextView.setText(results.get(0).getTitle()+"  confidence:"+results.get(0).getConfidence());
      }
    });

  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PHOTO_REQUEST_GALLERY){
      if (data!=null){
        Uri uri = data.getData();
        Bitmap bitmap = null;
        try {
          bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(),uri);
        } catch (IOException e) {
          e.printStackTrace();
        }
        System.out.println("Success!!!");
        mDiagnosisBitmap = scaleImage(bitmap);
        mImageView.setImageBitmap(mDiagnosisBitmap);

      }
    }else if (requestCode == PHOTO_REQUEST_CAMERA){
      if (hasSdcard()){
        Uri uri = Uri.fromFile(temFile);
        Bitmap bitmap = BitmapFactory.decodeFile(uri.getPath());
        mDiagnosisBitmap = scaleImage(bitmap);
        mImageView.setImageBitmap(mDiagnosisBitmap);
      }

    }else if (requestCode == PHOTO_REQUEST_CUT){
      if(data!=null){
        Bitmap bitmap = data.getParcelableExtra("data");
        mImageView.setImageBitmap(bitmap);
      }try {
        temFile.delete();
      }catch (Exception e){
        e.printStackTrace();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void gallery(View view){
    Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    startActivityForResult(intent, PHOTO_REQUEST_GALLERY);
  }

  private void camera(View view){
    Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
    if (hasSdcard()){
      temFile = new File(Environment.getExternalStorageDirectory(),PHOTO_FILE_NAME);
      Uri uri = Uri.fromFile(temFile);
      intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
    }
    startActivityForResult(intent, PHOTO_REQUEST_CAMERA);

  }

  private boolean hasSdcard(){
    if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
      return true;
    else
      return false;
  }

  public Bitmap scaleImage(Bitmap bitmap){
    int orignalWidth = bitmap.getWidth();
    int originalHeight = bitmap.getHeight();
    float scaleWidth = ((float)INPUT_SIZE)/orignalWidth;
    float scaleHeight = ((float)INPUT_SIZE)/originalHeight;
    Matrix matrix = new Matrix();
    matrix.postScale(scaleWidth,scaleHeight);
    Bitmap scaledBitmap = Bitmap.createBitmap(bitmap,0,0,orignalWidth,originalHeight,matrix,true);
    return scaledBitmap;
  }
}
