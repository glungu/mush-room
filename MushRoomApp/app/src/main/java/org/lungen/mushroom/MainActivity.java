package org.lungen.mushroom;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity
                          implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int REQUEST_CAPTURE_IMAGE = 1;
    private String tempFilePath;

    public static String serverURL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        setupSharedPreferences();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        // R.menu.settings_menu:
        // R: 'res' resource directory
        // menu: 'menu' sub-directory of the resource directory
        // settings_menu: 'settings_menu.xml' resource file
        menuInflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            // Main activity is parent of Settings activity (see AndroidManifest.xml)
            // Pressing 'Back' from child will go back to parent activity
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("server_url")) {
            serverURL = sharedPreferences.getString(
                    getResources().getString(R.string.pref_server_url_key),
                    getResources().getString(R.string.settings_server_url_default));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        serverURL = sharedPreferences.getString(
                getResources().getString(R.string.pref_server_url_key),
                getResources().getString(R.string.settings_server_url_default));
    }

    /** Called when the user taps the Start Camera button */
    public void buttonStartCameraClick(View view) {
        // clear previous results
        TextView resultTextView = findViewById(R.id.resultTextView);
        resultTextView.setText("");

        Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (pictureIntent.resolveActivity(getPackageManager()) != null) {
            /*
            File tempFile = createTempFile();
            tempFilePath = tempFile.getAbsolutePath();
            if (tempFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "org.lungen.mushroom.provider", tempFile);
                pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                pictureIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 200000);
                startActivityForResult(pictureIntent, REQUEST_CAPTURE_IMAGE);
            }
            */
            startActivityForResult(pictureIntent, REQUEST_CAPTURE_IMAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE_IMAGE && resultCode == RESULT_OK) {
            // without setting file URI in intent's extra
            if (data != null && data.getExtras() != null) {
                Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                ImageView imgView = findViewById(R.id.imgView);
                imgView.setBackgroundColor(Color.argb(0, 0, 0, 0));
                imgView.setImageBitmap(imageBitmap);

                // save to file
                File tempFile = createTempFile();
                tempFilePath = tempFile.getAbsolutePath();
                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(tempFile);
                    // PNG is a lossless format, the compression factor (100) is ignored
                    imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            /*
            ImageView imgView = findViewById(R.id.imgView);
            Glide.with(this).load(tempFilePath).into(imgView);
             */
        }

    }

    /** Called when the user taps the Analyze Image button */
    public void buttonAnalyzeImageClick(View view) {
        try {

            File tempFileToUpload = new File(tempFilePath);
            if (!tempFileToUpload.isFile()) {
                throw new RuntimeException("File: " + tempFilePath + ", does not exist");
            }
            if (tempFileToUpload.length() <= 0) {
                throw new RuntimeException("File: " + tempFilePath + ", length: " + tempFilePath.length());
            }

            final Button buttonAnalyze = findViewById(R.id.buttonAnalyze);
            buttonAnalyze.setText("Analyzing...");

            MultipartUploader.uploadToServer(tempFilePath, new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    SortedMap<Double, String> results = new TreeMap<>();
                    String errorMsg = null;

                    try {
                        String content = ((ResponseBody) response.body()).string();
                        System.out.println("### Response body: \n" + content);
                        JSONObject json = new JSONObject(content);

                        JSONArray predictions = json.getJSONArray("predictions");
                        for (int i = 0; i < predictions.length(); i++) {
                            JSONArray p = (JSONArray) predictions.get(i);
                            results.put(p.getDouble(1), p.getString(0));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMsg = e.getMessage();
                    } finally {
                        buttonAnalyze.setText("Analyze Image");
                    }

                    StringBuilder textToShow = new StringBuilder();

                    Double maxValue1 = results.lastKey();
                    if (maxValue1 < 0.5) {
                        textToShow.append("Не очень похоже на грибы").append('\n')
                                .append(results.get(maxValue1)).append(": ")
                                .append(Math.round(maxValue1 * 100)).append("%");
                    } else {
                        textToShow.append(results.get(maxValue1)).append(": ")
                                .append(Math.round(maxValue1 * 100)).append("%");
                        results.remove(maxValue1);

                        Double maxValue2 = results.lastKey();
                        if (maxValue1 - maxValue2 < 0.2) {
                            textToShow.append("\n")
                                    .append(results.get(maxValue2)).append(": ")
                                    .append(Math.round(maxValue2 * 100)).append("%");
                        }
                    }






                    TextView resultTextView = findViewById(R.id.resultTextView);
                    resultTextView.setText(textToShow.toString());
                }

                @Override
                public void onFailure(Call call, Throwable t) {
                    TextView textView = findViewById(R.id.resultTextView);
                    textView.setText("Error:" + t.getMessage());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final String TEMP_FILENAME = "temp0000";

    private File createTempFile() {
        try {
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(TEMP_FILENAME,".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


}
