/**
 * Copyright 2016 Ricardo Barbedo
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

package com.barbedo.dwall.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import com.barbedo.dwall.R;
import com.barbedo.dwall.data.DWallApplication;
import com.barbedo.dwall.data.Wallpaper;
import com.barbedo.dwall.data.WallpaperData;
import com.barbedo.dwall.fragments.TimePickerFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Selects the wallpaper and configures its properties.
 *
 * This activity is used to select the wallpaper from the system gallery and to set what is
 * the desired mode and parameters.
 *
 * The selected wallpaper is saved to the internal storage with a unique name and a thumbnail is
 * generated. When all the fields are filled and the ok button is pressed, the activity saves the
 * information on the database and returns to the list activity.
 *
 * @author Ricardo Barbedo
 */
public class EditActivity extends AppCompatActivity
        implements TimePickerFragment.OnTimeSetListener, AdapterView.OnItemSelectedListener {

    private final String TAG = "EditActivity";

    // Constants
    public static final int START_TIME_PICKER = 1;
    public static final int END_TIME_PICKER = 2;
    private final int DEFAULT_POSITION = 0;
    private static final int THUMB_WIDTH = 108;
    private static final int THUMB_HEIGHT = 192;
    private static final int SELECT_PICTURE = 100;

    private String selectedImagePath;

    private DWallApplication dWallApplication;
    private WallpaperData wallpaperData;
    private Wallpaper wallpaper;
    private ImageView preview;
    private Button okButton;
    private EditText nameEdit;
    private Spinner spinner;

    /**
     * Configures the UI.
     *
     * @param savedInstanceState Bundle with saved state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        preview = (ImageView) findViewById(R.id.preview);
        okButton = (Button) findViewById(R.id.ok_button);
        okButton.setEnabled(false);
        nameEdit = (EditText) findViewById(R.id.name_edit);

        wallpaper = new Wallpaper();
        dWallApplication = (DWallApplication) getApplication();
        wallpaperData = dWallApplication.getWallpaperData();

        // Gets desired position from the intent
        Intent intent = getIntent();
        wallpaper.setPosition(intent.getIntExtra(ListActivity.EXTRA_POSITION, DEFAULT_POSITION));

        // TODO: Check the database to see if the wallpaper at the specified position already exists

        // Populate spinner
        spinner = (Spinner) findViewById(R.id.mode_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.spinner_text, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setOnItemSelectedListener(this);
        spinner.setAdapter(adapter);

        Log.d(TAG, "onCreate");
    }

    /**
     * Saves the desired wallpaper to the database and returns to the main list.
     *
     * @param v Ok button.
     */
    public void registerWallpaper(View v) {

        // Protects the button if a field is empty
        String name = nameEdit.getText().toString();
        if (name.equals("")) {
            Snackbar.make(v, "Please, select a name.", Snackbar.LENGTH_SHORT)
                    .show();
        } else if (spinner.getSelectedItemPosition() == 0) {
            Snackbar.make(v, "Please, select a mode.", Snackbar.LENGTH_SHORT)
                    .show();
        } else {
            // Commit the selected wallpaper to the database
            wallpaper.setName(name);
            wallpaper.setMode(spinner.getSelectedItem().toString());
            wallpaperData.insertWallpaper(wallpaper);

            Log.d(TAG, "Wallpaper saved: " + wallpaper.toString());

            Intent intent = new Intent(this, ListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  // Clears stack
            startActivity(intent);
        }
    }


    /**
     * Launches the gallery activity to select the desired image.
     *
     * @param v Select image button
     */
    public void launchGallery(View v) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,
                "Select Picture"), SELECT_PICTURE);
    }

    /**
     * Receives and treats the data returned by the gallery activity.
     *
     * @param requestCode Our specified constant, to be sure that we were the ones that requested.
     * @param resultCode  Result code passed by the gallery activity.
     * @param data        Data containing the path of the selected image.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {

            // Delete files from last selection
            wallpaperData.deleteWallpaper(getApplicationContext(), wallpaper);

            // Retrieves URI
            Uri uri = data.getData();

            // Filename is a (unique) timestamp
            Long tsLong = System.currentTimeMillis() / 1000;
            String filename = tsLong.toString();
            String filenameThumb = filename + "_th";

            // Copies to internal data
            try {
                InputStream input = getContentResolver().openInputStream(uri);
                FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE);
                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                int len = 0;
                while ((len = input.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                if (fos != null)
                    fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            File filePath = getFileStreamPath(filename);
            Log.d(TAG, "Internal filepath: " + filePath.toString());

            // Create a thumbnail
            Bitmap thumbnail = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(filePath.getAbsolutePath()),
                    THUMB_WIDTH, THUMB_HEIGHT);
            try {
                FileOutputStream fos = openFileOutput(filename + "_th", Context.MODE_PRIVATE);
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Displays thumbnail
            preview.setImageDrawable(Drawable.
                    createFromPath(getFileStreamPath(filename + "_th").getAbsolutePath()));

            // Saves the filename reference
            wallpaper.setFilename(filename);
            Log.d(TAG, "Wallpaper filename: " + filename);

            // Enable the button
            okButton.setEnabled(true);
        }
    }

    /**
     * Time picker callback for when a time is selected.
     * Method of the implemented interface.
     *
     * @param id        Identifier of the picker (start or end).
     * @param hourOfDay Selected hour with a 24h system.
     * @param minute    Selected minute.
     *
     * @see com.barbedo.dwall.fragments.TimePickerFragment.OnTimeSetListener
     */
    public void onTimeSelected(int id, int hourOfDay, int minute) {

        switch (id) {
            // Launches the end time picker and saves the start time info
            case START_TIME_PICKER:
                DialogFragment newFragment = TimePickerFragment.newInstance(END_TIME_PICKER);
                newFragment.show(getSupportFragmentManager(), "timePicker");
                break;

            // Saves the end time info
            case END_TIME_PICKER:
                break;
        }
    }

    /**
     * Spinner listener callback when an item is selected.
     *
     * @param parent List of the spinner items.
     * @param view   View of the selected item.
     * @param pos    Position of the selected item.
     * @param id     Row id of the selected item.
     */
    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {

        // Launch the time picker if the time mode is selected from the menu
        if (parent.getItemAtPosition(pos).toString().equals("Time")) {
            DialogFragment newFragment = TimePickerFragment.newInstance(START_TIME_PICKER);
            newFragment.show(getSupportFragmentManager(), "timePicker");
        }
    }


    /**
     * Spinner listener callback when the user touches outside of the dropdown menu
     *
     * @param parent
     */
    public void onNothingSelected(AdapterView<?> parent) {
        // Ignore
    }
}


