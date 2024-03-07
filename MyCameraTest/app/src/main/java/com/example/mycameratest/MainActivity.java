package com.example.mycameratest;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import com.example.mycameratest.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private final String LOG_TAG = MainActivity.class.getSimpleName(); // MainActivity

    //for file write and read permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final int PHOTO_CAMERA_PERMISSIN_CODE = 2;
    private String tempPhotoPath = null;
    private int imageQuality = 50;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewInit();

        //if there is no read/write permission or storage access
        if(!isWriteReadPermission()){
            ActivityCompat.requestPermissions(this,PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
        }
    }
    private void viewInit(){
        binding.btnOpenCamera.setOnClickListener(view -> {
            // Let's write the function to open the camera here
            checkCameraPermission();
        });

        binding.btnSavePicture.setOnClickListener(view -> {
            if(tempPhotoPath!=null){
                Bitmap bitmap = rotateImageIfRequried(BitmapFactory.decodeFile(tempPhotoPath),tempPhotoPath);

                //You can give dynamic name
                saveBitmapToPicture(bitmap,getNewImageName());
            }
        });

        binding.seekbarImgQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                imageQuality = i;
                binding.txtQuality.setText("Image Quality: "+i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void saveBitmapToPicture(Bitmap bitmap, String fileName) {

        /*
Note:
Environment.getExternalStoragePublicDirectory() is used to
access public storage, while Context.getExternalFilesDir() is
used to access the application's own private storage. Which
one you should use depends on your application needs and usage scenario.
         */

        // picturesDirectory = The main directory where the image will be saved
        String picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+"/SercanCameraTest/";
        isCreateFolder(picturesDirectory);
        File imageFile = new File(picturesDirectory,fileName+".jpg");
        try {
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG,imageQuality,fos);
            fos.close();
            Log.d(LOG_TAG,"Saved Picture: "+imageFile.getAbsolutePath());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //Lets solve the problem of the image turning upside down white saving
    /*
Note:
Image rotation sideways when taking or uploading
a picture on Android may be caused by the device's
default camera app not handling EXIF metadata correctly.
To fix this situation, you may need to take this metadata
into account when processing the image.
     */
    private Bitmap rotateImageIfRequried(Bitmap bitmap,String path){
        ExifInterface ei;
        try {
            ei = new ExifInterface(path);

        }catch (Exception e){
            e.printStackTrace();
            return bitmap;
        }

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        switch (orientation){
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(bitmap,90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(bitmap,180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(bitmap,270);
            default: return bitmap;
        }
    }
    private Bitmap rotateImage(Bitmap bitmap,int degree){
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
    }
    private void isCreateFolder(String path){
        File dirFile = new File(path);
        if(!dirFile.exists()){
            dirFile.mkdirs();
        }
    }

    // Function that control camera permission
    private void checkCameraPermission(){
        if(checkPermissionControl(Manifest.permission.CAMERA)){
            //function to open the camera
            openCamera();
        } else{
            // if there is no camera permission
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},PHOTO_CAMERA_PERMISSIN_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PHOTO_CAMERA_PERMISSIN_CODE){
            if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                //function to open the camera
                openCamera();
            } else {
                Toast.makeText(MainActivity.this, "Camera Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void openCamera(){
        // Before turning on the camera, we must ask for permission to write files. Because it may not have given permissions before or it
        // may have been closed.
        if(!isWriteReadPermission()){
            ActivityCompat.requestPermissions(this,PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            return;
        }
        // if there is permission
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        /*
        NOTE: In order to capture a high resolution image from the camera, we must first create a file. Because high quality image (mb) is
        high size. The load the intent object can carry is low. For this reason, we should continue by creating a temporary file, not the official
        intent.
         */
        File photoTempFile = createImageFile();

        /*

        Note:
In Android Studio, FileProvider is an element that allows your
application to share files with other applications. In Android 7.0 (API level 24)
and later, the use of file:// URIs is restricted to increase application
security and isolation. Therefore, it is important to use FileProvider
to share files with other applications.

Using FileProvider, you can share your application's files with other
applications, but access is provided via content:// URIs, which are more
secure in terms of security.
         */
        if(photoTempFile!=null){
            Uri photoUri = FileProvider.getUriForFile(
                    this,
                    MainActivity.this.getPackageName()+".fileprovider",
                    photoTempFile
            );
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
            //We will use launcher to capture the image from the camera.
            cameraLauncher.launch(takePictureIntent);
        }
    }
    ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult o) {
                    File imageFile = new File(tempPhotoPath);
                    if(imageFile.exists()){
                        binding.imgShowCapturePhoto.setImageURI(Uri.parse(tempPhotoPath));
                    }
                }
            });
    private File createImageFile() {
        String imageFileName = getNewImageName();

        /*
Note: The Environment.getExternalStoragePublicDirectory() method is already
deprecated in Android 10 (API level 29) and above. So, when you try to
access external storage using this method, you may not get this type of error
on versions with API level 29 and above, but you may get errors on versions
below API level 29.

On older versions, you may need to use a different method to access external
storage. You can use the Environment.getExternalStorageDirectory() method
for this. However, note that this method is also limited as of Android 10.

Context.getExternalFilesDir() method is recommended for file operations on
Android 10 and above. This method returns a special directory on the
application's external storage and provides a secure way to store your
application's files in this directory.
         */
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        try {
            File image = File.createTempFile(
                    imageFileName,
                    ".jpg",
                    storageDir
                    );
            Log.d(LOG_TAG,"CreateImageFile: "+image.getAbsolutePath());
            //We need to keep the temporary file path.
            tempPhotoPath = image.getAbsolutePath();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getNewImageName(){
        return  "JPEG_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
    }


    //we write a function that checks whether your device has read and write permissions
    private boolean isWriteReadPermission(){
        return isExternalStrogeWritable()
                && checkPermissionControl(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                && checkPermissionControl(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private boolean isExternalStrogeWritable(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            return Environment.isExternalStorageManager();
        }
        return Environment.MEDIA_MOUNTED.equals((Environment.getExternalStorageState()));
    }

    private boolean checkPermissionControl(String permission){
        int check = ContextCompat.checkSelfPermission(this,permission);
        return check == PackageManager.PERMISSION_GRANTED;
    }
}