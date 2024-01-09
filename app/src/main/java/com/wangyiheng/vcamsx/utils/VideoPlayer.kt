package com.wangyiheng.vcamsx.utils

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.wangyiheng.vcamsx.MainHook
import com.wangyiheng.vcamsx.MainHook.Companion.TAG
import com.wangyiheng.vcamsx.MainHook.Companion.c2_reader_Surfcae
import com.wangyiheng.vcamsx.MainHook.Companion.context
import com.wangyiheng.vcamsx.MainHook.Companion.original_c1_preview_SurfaceTexture
import com.wangyiheng.vcamsx.MainHook.Companion.original_preview_Surface
import com.wangyiheng.vcamsx.utils.InfoProcesser.videoStatus
import tv.danmaku.ijk.media.player.IjkMediaPlayer

object VideoPlayer {
    var c2_hw_decode_obj: VideoToFrames? = null
    var ijkMediaPlayer: IjkMediaPlayer? = null
    var mediaPlayer: MediaPlayer? = null
    var c3_player: MediaPlayer? = null
    var copyReaderSurface:Surface? = null
    // 公共配置方法
    private fun configureMediaPlayer(mediaPlayer: IjkMediaPlayer) {
        mediaPlayer.apply {
            // 公共的错误监听器
            setOnErrorListener { _, what, extra ->
                Log.e("IjkMediaPlayer", "Error occurred. What: $what, Extra: $extra")
                Toast.makeText(context, "播放错误: $what", Toast.LENGTH_SHORT).show()
                true
            }

            // 公共的信息监听器
            setOnInfoListener { _, what, extra ->
                true
            }
        }
    }

    // RTMP流播放器初始化
    fun initRTMPStreamPlayer() {
        ijkMediaPlayer = IjkMediaPlayer().apply {
            // 硬件解码设置
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)

            // 缓冲设置
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec_mpeg4", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "analyzemaxduration", 100L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "probesize", 1024L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "flush_packets", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)

            // 应用公共配置
            configureMediaPlayer(this)

            // 设置 RTMP 流的 URL
            dataSource = videoStatus!!.liveURL

            // 异步准备播放器
            prepareAsync()

            // 准备好后的操作
            setOnPreparedListener {
                original_preview_Surface?.let { setSurface(it) }
                Toast.makeText(context, "直播接收成功", Toast.LENGTH_SHORT).show()
                start()
            }
        }
    }


    fun initMediaPlayer(surface:Surface){
        val volume = if (videoStatus?.volume == true) 1F else 0F
        mediaPlayer = MediaPlayer().apply {
            isLooping = true
            setSurface(surface)
            setVolume(volume,volume)
            setOnPreparedListener { start() }
            val videoPathUri = Uri.parse("content://com.wangyiheng.vcamsx.videoprovider")
            context?.let { setDataSource(it, videoPathUri) }
            prepare()
        }
    }



    fun initializeTheStateAsWellAsThePlayer(){
        InfoProcesser.initStatus()

        if(ijkMediaPlayer == null){
            if(videoStatus?.isLiveStreamingEnabled == true){
                initRTMPStreamPlayer()
            }
        }
    }


    private fun handleMediaPlayer(surface: Surface) {
        try {
            // 数据初始化
            InfoProcesser.initStatus()
            if(videoStatus?.isVideoEnable == false) return

            videoStatus?.let { status ->
                val volume = if (status.isVideoEnable && status.volume) 1F else 0F
                if(status.isLiveStreamingEnabled){
                    ijkMediaPlayer?.apply {
                        setVolume(volume, volume)
                        setSurface(surface)
                    }
                }else{
                    releaseMediaPlayer()
                    // 播放器存在就播放视频，不存在就创建播放器重新播放
                    mediaPlayer?.takeIf { it.isPlaying }?.apply {
                        setVolume(volume, volume)
                        setSurface(surface)
                    } ?: initMediaPlayer(surface)
                }

            }
        } catch (e: Exception) {
            // 这里可以添加更详细的异常处理或日志记录
            e.printStackTrace()
        }
    }

    fun releaseMediaPlayer(){
        if(mediaPlayer == null)return
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun ijkplay_play() {
        // 带name的surface
        original_preview_Surface?.let { surface ->
            handleMediaPlayer(surface)
        }

        // name=null的surface
        c2_reader_Surfcae?.let { surface ->
            c2_reader_play(surface)
        }
    }

    fun c1_camera_play() {
        if (original_c1_preview_SurfaceTexture != null && videoStatus?.isVideoEnable == true) {
            original_preview_Surface = Surface(original_c1_preview_SurfaceTexture)
            if(original_preview_Surface!!.isValid == true){

                handleMediaPlayer(original_preview_Surface!!)
            }
        }

        c2_reader_Surfcae?.let { surface ->
            c2_reader_play(surface)
        }

    }

    fun c2_reader_play(c2_reader_Surfcae:Surface){
        if(c2_reader_Surfcae == copyReaderSurface){
            return
        }

        copyReaderSurface = c2_reader_Surfcae

        if(c2_hw_decode_obj != null){
            c2_hw_decode_obj!!.stopDecode()
            c2_hw_decode_obj = null
        }

        c2_hw_decode_obj = VideoToFrames()
        try {
            val videoUrl = "content://com.wangyiheng.vcamsx.videoprovider"
            val videoPathUri = Uri.parse(videoUrl)
            c2_hw_decode_obj!!.setSaveFrames(OutputImageFormat.NV21)
            c2_hw_decode_obj!!.set_surface(c2_reader_Surfcae)
            c2_hw_decode_obj!!.decode(videoPathUri)

        }catch (e:Exception){
            Log.d("dbb",e.toString())
        }
    }

}