package com.valdioveliu.valdio.audioplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String Broadcast_PLAY_NEW_AUDIO = "com.valdioveliu.valdio.audioplayer.PlayNewAudio";
    boolean serviceBound = false;
    List<Audio> audioList;
    ImageView collapsingImageView;
    int imageIndex = 0;
    private MediaPlayerService player;
    private MediaMetadataRetriever mmr = new MediaMetadataRetriever();

    private static int sId = 100;


    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        collapsingImageView = (ImageView) findViewById(R.id.collapsingImageView);

        loadCollapsingImage(imageIndex);
        requestPermission();
        audioList = findAllSong(AudioUtils.paths);
        initRecyclerView();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg");
                //play the first audio in the ArrayList
//                playAudio(2);
                if (imageIndex == 4) {
                    imageIndex = 0;
                    loadCollapsingImage(imageIndex);
                } else {
                    loadCollapsingImage(++imageIndex);
                }
            }
        });

    }

    private void requestPermission() {
        boolean isPermitted = true;
        String[] permissions = {
                android.Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        for (int i = 0; i < permissions.length; i++) {
            if(ContextCompat.checkSelfPermission(this, permissions[i])
                    != PackageManager.PERMISSION_GRANTED){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(permissions, 100);
                    isPermitted = false;
                }
            }
        }
        if(isPermitted){
            Log.e("POSITION", "requestPermission: GO HERE!");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 100:{
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Log.e("PERMISSION", "requestPermission: PERMITTED" );
                    findAllSong(AudioUtils.paths);
                } else {
//                    requestPermission(); // request again
                }
                return;
            }
        }
    }

    private void initRecyclerView() {
        if (audioList.size() > 0) {
            RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
            RecyclerView_Adapter adapter = new RecyclerView_Adapter(audioList, getApplication());
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.addOnItemTouchListener(new CustomTouchListener(this, new onItemClickListener() {
                @Override
                public void onClick(View view, int index) {
                    playAudio(index);
                }
            }));

        }
    }

    private void loadCollapsingImage(int i) {
        TypedArray array = getResources().obtainTypedArray(R.array.images);
        collapsingImageView.setImageDrawable(array.getDrawable(i));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    private void playAudio(int audioIndex) {
        //Check is service is active
        if (!serviceBound) {
            //Store Serializable audioList to SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);

            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            //Store the new audioIndex to SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);

            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }
    }


//    private void loadAudio() {
//        ContentResolver contentResolver = getContentResolver();
//
//        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
//        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
//        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
//        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);
//        audioList = new ArrayList<>();
//        if (cursor != null && cursor.getCount() > 0) {
//
//            while (cursor.moveToNext()) {
//                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
//                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
//                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
//                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
//
//                // Save to audioList
//                audioList.add(new Audio(data, title, album, artist));
//            }
//        }
//        cursor.close();
//    }

    private ArrayList<HashMap<String, String>> getPlaylist(String path) {
        ArrayList<HashMap<String, String>> playlist = new ArrayList<>();
        HashMap<String, String> aSong = null;
        try {
            File rootFolder = new File(path);
            File[] files = rootFolder.listFiles();// get all file from a folder
            if (files != null) {
                ArrayList<HashMap<String, String>> list;
                for (File file : files) {
                    String absolutePath = file.getAbsolutePath();
                    if (file.isDirectory()) {
                        list = getPlaylist(absolutePath);
                        if (list != null) {
                            playlist.addAll(list);
                        }
                    } else if (file.getName().endsWith(".mp3")) {
                        aSong = new HashMap<>();
                        aSong.put("file_path", absolutePath);
                        playlist.add(aSong);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return playlist;
    }

    private List<Audio> findAllSong(String[] myPath) {
        List<Audio> playList = new ArrayList<>();
        for (String path : myPath) {
            ArrayList<HashMap<String, String>> songs = getPlaylist(path);
            if (songs != null) {
                int size = songs.size();
                String filePath = "";
                for (int i = 0; i < size; i++) {
                    filePath = songs.get(i).get("file_path");
                    // extract info form a song
                    Audio song = extractSongInfo(filePath);
                    playList.add(song);
//                    Log.e("SONG TITLE", "findAllSong: "+song.getTitle());
                }
            }
        }

        return playList;
    }

    private Audio extractSongInfo(String filePath) {
        mmr.setDataSource(filePath);
        String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String stringDuraion = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        String data = filePath;
        String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

        Bitmap bitmap = null;
        byte[] artImg = mmr.getEmbeddedPicture();
        if (artImg != null) {
            bitmap = BitmapFactory.decodeByteArray(artImg, 0, artImg.length);
        }
        if (bitmap == null) {
            // it alway must be context.getResources()
            bitmap = BitmapFactory.decodeResource(getResources(),
                    R.drawable.image1);
        }
        String id = sId++ + "";
        int duraion = Integer.parseInt(stringDuraion);
        /*
        String id, String data, String title, String album, String artist
         */
        Audio song = new Audio(id, data, title, album,artist);

        if (title == null) {
            String[] songNames = filePath.split("/");
            int len = songNames.length;
            String name = songNames[len - 1];
            int nameLen = name.length();
            song.setTitle(name.substring(0, nameLen - 4));
        }

        return song;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            //service is active
            player.stopSelf();
        }
    }
}
