import android.media.MediaPlayer

class Sound (val url: String, val directionVector: FloatArray) : MediaPlayer.OnPreparedListener {

    private var mediaPlayer: MediaPlayer? = null

    /** Initialise media player and prepare to play */
    fun startPlaying() {
        // Initialise media player and prepare
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener(this@Sound)
            prepareAsync()
        }
    }

    /** Called when media player is ready */
    override fun onPrepared(mp: MediaPlayer?) {

    }

    /** Set media player volume */
    fun setVolume() {

    }

    /** Stop playing and release media player */
    fun stopPlaying() {

    }
}