package com.duan.musicoco.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.util.Log;

import com.duan.musicoco.aidl.Song;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Created by DuanJiaNing on 2017/5/23.
 */

public class PlayController {

    private static final String TAG = "PlayController";

    private Context context;

    private int mCurrentSong = 0;

    private int mPlayState;

    private static volatile PlayController MANAGER = null;

    private List<Song> mPlayList = Collections.synchronizedList(new ArrayList<Song>());

    private final MediaPlayer mPlayer;

    private boolean isNext = true;
    private int mPlayListId;

    public int getPlayListId() {
        return mPlayListId;
    }

    public interface NotifyStatusChanged {
        void notify(Song song, int index, int status);
    }

    public interface NotifySongChanged {
        void notify(Song song, int index, boolean isNext);
    }

    public interface NotifyPlayListChanged {
        void notify(Song current, int index, int id);
    }

    private final NotifySongChanged mNotifySongChanged;
    private final NotifyPlayListChanged mNotifyPlayListChanged;
    private final NotifyStatusChanged mNotifyStatusChanged;

    //未知错误
    public static final int ERROR_UNKNOWN = -1;

    public static final int ERROR_INVALID = -2;

    //歌曲文件解码错误
    public static final int ERROR_DECODE = -3;

    //没有指定歌曲
    public static final int ERROR_NO_RESOURCE = -4;

    //正在播放
    public static final int STATUS_PLAYING = 10;

    //播放结束
    public static final int STATUS_COMPLETE = 11;

    //开始播放
    public static final int STATUS_START = 12;

    //播放暂停
    public static final int STATUS_PAUSE = 13;

    //停止
    public static final int STATUS_STOP = 14;

    //默认播放模式，列表播放，播放至列表末端时停止播放
    public static final int MODE_DEFAULT = 20;

    //列表循环
    public static final int MODE_LIST_LOOP = 21;

    //单曲循环
    public static final int MODE_SINGLE_LOOP = 22;

    //随机播放
    public static final int MODE_RANDOM = 23;

    private int mPlayMode = MODE_DEFAULT;

    private PlayController(Context context, NotifyStatusChanged sl, NotifySongChanged sc, NotifyPlayListChanged pl) {

        this.context = context;
        this.mNotifyStatusChanged = sl;
        this.mNotifySongChanged = sc;
        this.mNotifyPlayListChanged = pl;
        mPlayState = STATUS_STOP;

        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                nextSong();
            }
        });

    }

    public static PlayController getMediaController(Context context, NotifyStatusChanged sl, NotifySongChanged sc, NotifyPlayListChanged pl) {
        if (MANAGER == null) {
            synchronized (PlayController.class) {
                if (MANAGER == null)
                    MANAGER = new PlayController(context, sl, sc, pl);
            }
        }
        return MANAGER;
    }

    //设置播放模式
    public void setPlayMode(int mode) {
        this.mPlayMode = mode;
    }

    //获得播放模式
    public int getPlayMode() {
        return this.mPlayMode;
    }

    //返回播放列表
    public List<Song> getSongsList() {
        return mPlayList;
    }

    //设置播放列表
    public Song setPlayList(List<Song> songs, int current, int id) {
        this.mPlayList = songs;
        this.mPlayListId = id;

        Song currentS = songs.get(mCurrentSong);
        mNotifyPlayListChanged.notify(currentS, current, id);

        mCurrentSong = current;
        changeSong();

        return currentS;
    }

    //当前正在播放曲目
    public Song getCurrentSong() {
        return mPlayList.size() == 0 ? null : mPlayList.get(mCurrentSong);
    }

    public int getCurrentSongIndex() {
        return mCurrentSong;
    }

    //播放指定曲目
    public int play(@NonNull Song song) {
        return play(mPlayList.indexOf(song));
    }

    public int play(int index) {
        int result = ERROR_INVALID;
        if (index != -1) { //列表中有该歌曲
            if (mCurrentSong != index) { //不是当前歌曲
                isNext = mCurrentSong < index;
                mCurrentSong = index;
                if (mPlayState != STATUS_PLAYING) {
                    mNotifyStatusChanged.notify(getCurrentSong(), mCurrentSong, STATUS_START);
                    mPlayState = STATUS_PLAYING; //切换并播放
                }
                result = changeSong();
            } else if (mPlayState != STATUS_PLAYING) { // 是但没在播放
                mPlayState = STATUS_PAUSE;
                resume();//播放
            } else  // 是且已经在播放
                return 1;
        } else return ERROR_NO_RESOURCE;
        return result;
    }

    public int prepare(@NonNull Song song) {
        int result = ERROR_INVALID;
        int index = mPlayList.indexOf(song);
        if (index != -1) { //列表中有该歌曲
            if (mCurrentSong != index) { //不是当前歌曲
                mCurrentSong = index;
                if (mPlayState == STATUS_PLAYING)
                    pause();
                result = changeSong();
            }
        } else return ERROR_NO_RESOURCE;
        return result;
    }

    //获得播放状态
    public int getPlayState() {
        return mPlayState;
    }

    //释放播放器，服务端停止时，该方法才应该被调用
    public void releaseMediaPlayer() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayState = STATUS_STOP;
        }
    }

    //上一曲
    public Song preSong() {
        isNext = false;
        switch (mPlayMode) {
            case MODE_SINGLE_LOOP: {
                changeSong();
                break;
            }
            case MODE_RANDOM: {
                int pre = new Random().nextInt(mPlayList.size());
                if (pre != mCurrentSong) {
                    mCurrentSong = pre;
                    changeSong();
                }
                break;
            }
            case MODE_LIST_LOOP:
            default: {
                if (mCurrentSong == 0) {
                    mCurrentSong = mPlayList.size() - 1;
                } else {
                    mCurrentSong--;
                }
                changeSong();

                break;
            }
        }

        return mPlayList.get(mCurrentSong);
    }

    //下一曲
    public Song nextSong() {
        isNext = true;
        switch (mPlayMode) {
            case MODE_SINGLE_LOOP: {
                changeSong();
                break;
            }
            case MODE_LIST_LOOP: {
                if (mCurrentSong == mPlayList.size() - 1) {
                    mCurrentSong = 0;
                } else {
                    mCurrentSong++;
                }
                changeSong();
                break;
            }
            case MODE_RANDOM: {
                int next = new Random().nextInt(mPlayList.size());
                if (next != mCurrentSong) {
                    mCurrentSong = next;
                    changeSong();
                }
                break;
            }
            default: {
                if (mCurrentSong == mPlayList.size() - 1) { // 最后一首
                    mCurrentSong = 0;
                    changeSong();
                    pause();//使暂停播放
                } else {
                    mCurrentSong++;
                    changeSong();
                }
                break;
            }
        }

        return mPlayList.get(mCurrentSong);
    }

    //暂停播放
    public int pause() {
        if (mPlayState == STATUS_PLAYING) {
            mPlayer.pause();
            mNotifyStatusChanged.notify(getCurrentSong(), mCurrentSong, STATUS_STOP);
            mPlayState = STATUS_PAUSE;
        }
        return mPlayState;
    }

    //继续播放
    public int resume() {
        if (mPlayState != STATUS_PLAYING) {
            mPlayer.start();
            mNotifyStatusChanged.notify(getCurrentSong(), mCurrentSong, STATUS_START);
            mPlayState = STATUS_PLAYING;
        }
        return 1;
    }

    //定位到
    public int seekTo(int to) {
        mPlayer.seekTo(to);
        return 1;
    }

    //获得播放进度
    public int getProgress() {
        return mPlayer.getCurrentPosition();
    }

    /**
     * 切换曲目
     *
     * @return 切换成功返回 1
     */
    private synchronized int changeSong() {

        if (mPlayState == STATUS_PLAYING || mPlayState == STATUS_PAUSE) {
            mPlayer.stop();
        }

        mPlayer.reset();

        String next = mPlayList.get(mCurrentSong).path;
        try {
            mPlayer.setDataSource(next);
            mPlayer.prepare();

        } catch (IOException e) {
            e.printStackTrace();
            return ERROR_DECODE;
        }

        if (mPlayState == STATUS_PLAYING) {
            mPlayer.start();
        }

        mNotifySongChanged.notify(getCurrentSong(), mCurrentSong, isNext);
        return 1;
    }

    //用于提取频谱
    public int getAudioSessionId() {
        return mPlayer.getAudioSessionId();
    }

    public void remove(Song song) {
        if (song == null)
            return;

        int index = mPlayList.indexOf(song);
        if (index != -1) {
            if (mCurrentSong == index) {
                int tempS = mPlayMode;
                mPlayMode = MODE_LIST_LOOP;
                mPlayList.remove(index);
                mCurrentSong--;
                nextSong();
                mPlayMode = tempS;
            } else {
                mPlayList.remove(index);
                if (index < mCurrentSong) {
                    mCurrentSong--;
                }
            }
        }
    }

}
