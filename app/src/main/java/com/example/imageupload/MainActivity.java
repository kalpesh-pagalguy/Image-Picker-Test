package com.example.imageupload;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 999;
    private static final int CAMERA = 100;
    private static final int IMAGE = 101;

    ImageView imageView;
    Button camButton;
    Button galButton;
    private Uri cameraUri;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.image);
        camButton = (Button) findViewById(R.id.camera);
        galButton = (Button) findViewById(R.id.gallery);
        camButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestCameraPermission();
                        return;
                    }
                }
                openCamera();
            }
        });

        galButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
    }

    private void openCamera() {
        File cameraCacheFile = getCameraCacheFile(MainActivity.this, "123_" + System.currentTimeMillis() + ".png");
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraUri = Uri.fromFile(cameraCacheFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        startActivityForResult(takePictureIntent, CAMERA);
    }

    private void openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), IMAGE);
        } else {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), IMAGE);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("This App needs Camera permission");
            builder.setCancelable(false);
            builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    requestPermissions(new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA_PERMISSION);
                }
            });
            builder.show();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA) {
                imageUri = cameraUri;
                showResizeAndUpload();
            } else if (requestCode == IMAGE) {
                imageUri = data.getData();
                showResizeAndUpload();
            } else {
                Toast.makeText(MainActivity.this, "Some other intent result", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "User cancelled request", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResizeAndUpload() {
        Glide.with(this).load(imageUri).fitCenter().into(imageView);
        resize(imageUri)
                .flatMap(new Func1<File, Observable<String>>() {
                    @Override
                    public Observable<String> call(File file) {
                        //Return Observable to upload file.
                        return null;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(String s) {

                    }
                });
    }


    //Call this from non UI thread.
    private Observable<File> resize(final Uri uri) {
        return Observable.create(new Observable.OnSubscribe<File>() {
            @Override
            public void call(Subscriber<? super File> subscriber) {
                try {
                    BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();
                    mBitmapOptions.inJustDecodeBounds = true;

                    if (Build.VERSION.SDK_INT < 19) {
                        BitmapFactory.decodeFile(imageUri.getPath(), mBitmapOptions);
                    } else {
                        ParcelFileDescriptor parcelFileDescriptor;
                        parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
                        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, mBitmapOptions);
                        try {
                            parcelFileDescriptor.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    int srcWidth = mBitmapOptions.outWidth;
                    int srcHeight = mBitmapOptions.outHeight;

                    boolean isLandscape = srcWidth > srcHeight;
                    double scale = 1.0;
                    if (isLandscape) {
                        if (srcWidth > 1920) {
                            scale = 1920 * 1.0 / srcWidth;
                        }
                    } else {
                        if (srcHeight > 1920) {
                            scale = 1920 * 1.0 / srcHeight;
                        }
                    }

                    int destWidth = (int) (srcWidth * scale);
                    int destHeight = (int) (srcHeight * scale);

                    subscriber.onNext(Glide.with(getApplicationContext())
                            .load(uri)
                            .downloadOnly(destWidth, destHeight)
                            .get());
                    subscriber.onCompleted();

                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    public static File getCameraCacheFile(Context context, String fileName) {
        String cameraPath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            cameraPath = context.getExternalCacheDir() + "/" + fileName;
        else
            cameraPath = context.getCacheDir().getAbsolutePath() + "/" + fileName;
        File cameraCacheFile = new File(cameraPath);
        if (cameraCacheFile.exists()) cameraCacheFile.delete();
        return cameraCacheFile;
    }
}
